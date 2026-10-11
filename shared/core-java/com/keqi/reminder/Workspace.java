package com.keqi.reminder;

import org.json.*;
import java.util.*;

/** Shared task commands and causal field merging. The host persists each call atomically. */
public final class Workspace {
    public static final int PROTOCOL=1;
    private final JSONObject data;
    private static final Set<String> USER=new HashSet<>(Arrays.asList("title","manualDue","localDone","snoozeUntil","note","priority","tags","courseKey","courseTitle","deleted","removed","enabled","rule","paused","ends","template","retired"));
    private static final String[] SOURCE={"platform","courseTitle","sourceId","phase","title","kind","sourceUrl","status","due","precision","rawDeadline","deadlineObserved","deadlineCheckedAt","examStart","rawStart","firstSeen","lastSeen"};
    public Workspace(JSONObject saved) throws JSONException {
        data=new JSONObject(saved.toString());
        if(!data.has("workspace"))data.put("workspace",UUID.randomUUID().toString());
        if(!data.has("device"))data.put("device",UUID.randomUUID().toString());
        for(String k:new String[]{"entities","receipts","aliases"})if(!data.has(k))data.put(k,new JSONObject());
        if(!data.has("outbox"))data.put("outbox",new JSONArray());
        if(!data.has("epoch"))data.put("epoch",UUID.randomUUID().toString());
        if(!data.has("counter"))data.put("counter",0);
        if(!data.has("revision"))data.put("revision",0);
    }
    public JSONObject save() throws JSONException{return new JSONObject(data.toString());}
    public String id(){return data.optString("workspace");}
    public String device(){return data.optString("device");}
    public String epoch(){return data.optString("epoch");}
    public long revision(){return data.optLong("revision");}
    public JSONArray pending() throws JSONException{return new JSONArray(data.getJSONArray("outbox").toString());}
    public static String identity(String workspace,String platform,String account,String course,String source,String phase) {
        return Core.hash(new JSONArray(Arrays.asList("keqi-v1",workspace,platform,account,course,source,phase)).toString());
    }
    private JSONObject entity(String id,String type) throws JSONException {
        JSONObject entities=data.getJSONObject("entities");JSONObject e=entities.optJSONObject(id);
        if(e==null){e=new JSONObject().put("id",id).put("type",type).put("heads",new JSONObject()).put("observations",new JSONObject()).put("revision",0);entities.put(id,e);}
        if(!e.getString("type").equals(type))throw new IllegalArgumentException("对象类型不匹配");return e;
    }
    private JSONObject operation(String id,String type,JSONObject values) throws JSONException {
        if(values.length()==0)throw new IllegalArgumentException("没有需要保存的修改");
        JSONObject e=entity(id,type),fields=new JSONObject();Iterator<String> names=values.keys();
        while(names.hasNext()){String name=names.next();validateField(type,name,values.get(name));JSONArray bases=new JSONArray(),heads=e.getJSONObject("heads").optJSONArray(name);
            if(heads!=null)for(int i=0;i<heads.length();i++)bases.put(heads.getJSONObject(i).getString("op"));
            fields.put(name,new JSONObject().put("bases",bases).put("value",values.get(name)));
        }
        long counter=data.getLong("counter")+1;data.put("counter",counter);
        return new JSONObject().put("protocol",PROTOCOL).put("workspace",id()).put("device",device()).put("counter",counter).put("id",device()+":"+counter).put("entity",id).put("type",type).put("fields",fields);
    }
    public void edit(String id,String type,JSONObject values,boolean host) throws JSONException {edit(id,type,values,null,host);}
    public void edit(String id,String type,JSONObject values,JSONObject bases,boolean host) throws JSONException {
        JSONObject op=operation(id,type,values);if(bases!=null){Iterator<String> names=op.getJSONObject("fields").keys();while(names.hasNext()){String field=names.next();if(bases.has(field))op.getJSONObject("fields").getJSONObject(field).put("bases",bases.getJSONArray(field));}}
        apply(op,host);
        if(!host)data.getJSONArray("outbox").put(op);
    }
    public String nextOperationId(){return device()+":"+(data.optLong("counter")+1);}
    private static String canonicalJson(Object value) {
        if(value instanceof JSONObject){JSONObject object=(JSONObject)value;List<String> keys=new ArrayList<>();Iterator<String> names=object.keys();while(names.hasNext())keys.add(names.next());Collections.sort(keys);StringBuilder s=new StringBuilder("{");for(String key:keys){if(s.length()>1)s.append(',');s.append(JSONObject.quote(key)).append(':').append(canonicalJson(object.opt(key)));}return s.append('}').toString();}
        if(value instanceof JSONArray){JSONArray array=(JSONArray)value;StringBuilder s=new StringBuilder("[");for(int i=0;i<array.length();i++){if(i>0)s.append(',');s.append(canonicalJson(array.opt(i)));}return s.append(']').toString();}
        return value==null||value==JSONObject.NULL?"null":value instanceof String?JSONObject.quote(value.toString()):value.toString();
    }
    public static void validateField(String type,String field,Object value) {
        Map<String,List<String>> allowed=new HashMap<>();
        allowed.put("manual",Arrays.asList("title","manualDue","localDone","snoozeUntil","note","priority","tags","courseKey","courseTitle","deleted","baseline"));
        allowed.put("platform",Arrays.asList("manualDue","localDone","snoozeUntil","note","priority","tags"));
        allowed.put("course",Arrays.asList("removed","enabled"));
        allowed.put("series",Arrays.asList("rule","template","paused","deleted"));
        allowed.put("subtask",Arrays.asList("title","parent","done","position","deleted"));
        if(!allowed.containsKey(type)||!allowed.get(type).contains(field))throw new IllegalArgumentException("不支持的任务字段");
        if(value==JSONObject.NULL&&!Arrays.asList("manualDue","snoozeUntil").contains(field))throw new IllegalArgumentException("字段不能为空");
        if(field.equals("baseline")||field.equals("template")){if(!(value instanceof JSONObject)||field.equals("baseline")&&!type.equals("manual")||field.equals("template")&&!type.equals("series"))throw new IllegalArgumentException("生成模板无效");JSONObject template=(JSONObject)value;Iterator<String> keys=template.keys();while(keys.hasNext()){String k=keys.next();if(!Arrays.asList("title","manualDue","priority","tags","note","courseKey","courseTitle","seriesId","slot","originalDue","segment").contains(k))throw new IllegalArgumentException("模板包含不支持的字段");if(!Arrays.asList("seriesId","slot","originalDue","segment").contains(k))validateField("manual",k,template.opt(k));}}
        if(field.equals("rule")){try{if(!type.equals("series")||!(value instanceof JSONObject))throw new IllegalArgumentException("重复规则无效");Repeats.validate((JSONObject)value);}catch(JSONException e){throw new IllegalArgumentException("重复规则无效");}}
        if(value!=JSONObject.NULL){
            if(Arrays.asList("localDone","deleted","removed","enabled","paused","done","retired").contains(field)&&!(value instanceof Boolean))throw new IllegalArgumentException("状态格式不正确");
            if(Arrays.asList("manualDue","snoozeUntil","ends").contains(field)&&(!(value instanceof Number)||((Number)value).longValue()<946684800000L||((Number)value).longValue()>7258118400000L))throw new IllegalArgumentException("日期范围不正确");
            if(field.equals("priority")&&(!(value instanceof Number)||((Number)value).intValue()<0||((Number)value).intValue()>3||((Number)value).doubleValue()!=((Number)value).intValue()))throw new IllegalArgumentException("优先级范围不正确");
            if(Arrays.asList("title","note","parent","courseKey","courseTitle").contains(field)&&(!(value instanceof String)||((String)value).length()>(field.equals("note")?50000:500)))throw new IllegalArgumentException("文字长度或格式不正确");
            if(field.equals("title")&&value.toString().trim().isEmpty())throw new IllegalArgumentException("请填写任务标题");
            if(field.equals("tags")){if(!(value instanceof JSONArray)||((JSONArray)value).length()>30)throw new IllegalArgumentException("标签数量不正确");JSONArray tags=(JSONArray)value;for(int i=0;i<tags.length();i++)if(!(tags.opt(i) instanceof String)||tags.optString(i).length()>100)throw new IllegalArgumentException("标签格式不正确");}
            if(field.equals("position")&&(!(value instanceof Number)||!Double.isFinite(((Number)value).doubleValue())))throw new IllegalArgumentException("步骤顺序无效");
        }
    }
    public void apply(JSONObject op,boolean host) throws JSONException {
        if(op.getInt("protocol")!=PROTOCOL||!op.getString("workspace").equals(id()))throw new IllegalArgumentException("同步版本或工作区不匹配");
        String opId=op.getString("id"),sender=op.getString("device");long counter=op.getLong("counter");
        if(!sender.matches("[a-fA-F0-9-]{36}")||counter<1||!opId.equals(sender+":"+counter))throw new IllegalArgumentException("操作身份无效");
        String digest=Core.hash(canonicalJson(op));JSONObject receipts=data.getJSONObject("receipts"),receipt=receipts.optJSONObject(opId);
        if(receipt!=null){if(!receipt.getString("digest").equals(digest))throw new IllegalArgumentException("同一操作身份的内容已改变");return;}
        String type=op.getString("type");if(!Arrays.asList("manual","platform","course","series","subtask").contains(type))throw new IllegalArgumentException("对象类型无效");
        JSONObject fields=op.getJSONObject("fields");if(fields.length()>30)throw new IllegalArgumentException("修改字段过多");
        if(!op.getString("entity").matches("[a-fA-F0-9-]{36}|[a-f0-9]{32}|[a-f0-9]{64}"))throw new IllegalArgumentException("对象身份无效");
        JSONObject e=entity(op.getString("entity"),type),all=e.getJSONObject("heads");Iterator<String> names=fields.keys();
        while(names.hasNext()){String field=names.next();JSONObject patch=fields.getJSONObject(field);validateField(type,field,patch.get("value"));JSONArray bases=patch.getJSONArray("bases");
            for(int i=0;i<bases.length();i++)if(!receipts.has(bases.getString(i)))throw new IllegalArgumentException("needs_dependencies");
        }
        names=fields.keys();while(names.hasNext()){String field=names.next();JSONObject patch=fields.getJSONObject(field);JSONArray old=all.optJSONArray(field),kept=new JSONArray();Set<String> bases=new HashSet<>();JSONArray b=patch.getJSONArray("bases");for(int i=0;i<b.length();i++)bases.add(b.getString(i));
            if(old!=null)for(int i=0;i<old.length();i++)if(!bases.contains(old.getJSONObject(i).getString("op")))kept.put(old.getJSONObject(i));
            kept.put(new JSONObject().put("op",opId).put("value",patch.get("value")));all.put(field,kept);
        }
        receipts.put(opId,new JSONObject().put("digest",digest));
        if(host){long next=revision()+1;data.put("revision",next);e.put("revision",next);}
    }
    public void observe(String platform,String account,JSONArray courses,JSONArray tasks,boolean historical,boolean host) throws JSONException {
        if(account.isEmpty())return;
        for(int i=0;i<courses.length();i++){JSONObject c=courses.getJSONObject(i);String oldKey=c.getString("key"),key=identity(id(),platform,account,oldKey,"course","");
            JSONObject e=entity(key,"course");JSONObject record=new JSONObject(c.toString()).put("key",key).put("sourceKey",oldKey).put("sourceAccount",account);
            e.getJSONObject("observations").put(device(),new JSONObject().put("historical",historical).put("data",record));
            data.getJSONObject("aliases").put(oldKey,key);bump(e,host);
        }
        for(int i=0;i<tasks.length();i++){JSONObject t=tasks.getJSONObject(i);if(t.optString("sourceId").isEmpty())continue;
            String original=t.getString("id"),course=t.getString("courseKey"),key=identity(id(),platform,account,course,t.getString("sourceId"),t.optString("phase"));
            JSONObject e=entity(key,"platform"),record=new JSONObject();for(String field:SOURCE)if(t.has(field))record.put(field,t.get(field));
            record.put("id",key).put("sourceAccount",account).put("sourceCourseKey",course).put("courseKey",identity(id(),platform,account,course,"course",""));
            JSONObject previous=e.getJSONObject("observations").optJSONObject(device());if(previous!=null&&!t.optBoolean("deadlineObserved",true))for(String field:new String[]{"due","precision","rawDeadline","deadlineCheckedAt"})if(previous.getJSONObject("data").has(field))record.put(field,previous.getJSONObject("data").get(field));
            e.getJSONObject("observations").put(device(),new JSONObject().put("historical",historical).put("data",record));data.getJSONObject("aliases").put(original,key);bump(e,host);
        }
    }
    private void bump(JSONObject e,boolean host) throws JSONException {if(host){long next=revision()+1;data.put("revision",next);e.put("revision",next);}else e.put("dirty",true);}
    public String canonical(String key){return data.optJSONObject("aliases").optString(key,key);}
    public JSONObject get(String key) throws JSONException {JSONObject e=data.getJSONObject("entities").optJSONObject(canonical(key));return e==null?null:project(e);}
    private JSONObject project(JSONObject e) throws JSONException {
        JSONObject result=new JSONObject().put("id",e.getString("id")).put("entityType",e.getString("type"));JSONObject observations=e.getJSONObject("observations");List<JSONObject> records=new ArrayList<>();Iterator<String> devices=observations.keys();
        while(devices.hasNext())records.add(observations.getJSONObject(devices.next()));records.sort(Comparator.<JSONObject,Boolean>comparing(o->o.optBoolean("historical")).thenComparing(o->-o.optJSONObject("data").optLong("lastSeen")).thenComparing(o->canonicalJson(o.optJSONObject("data"))));
        if(!records.isEmpty())result=new JSONObject(records.get(0).getJSONObject("data").toString()).put("entityType",e.getString("type"));
        JSONArray conflicts=new JSONArray();JSONObject heads=e.getJSONObject("heads");JSONArray baselines=heads.optJSONArray("baseline");if(baselines!=null&&baselines.length()>0){JSONObject baseline=baselines.getJSONObject(0).getJSONObject("value");Iterator<String> bk=baseline.keys();while(bk.hasNext()){String key=bk.next();result.put(key,baseline.get(key));}}Iterator<String> fields=heads.keys();
        while(fields.hasNext()){String field=fields.next();JSONArray versions=heads.getJSONArray(field);if(versions.length()==0)continue;Object value=versions.getJSONObject(0).get("value");boolean conflict=false;
            for(int i=1;i<versions.length();i++)if(!Objects.equals(canonicalJson(value),canonicalJson(versions.getJSONObject(i).get("value"))))conflict=true;
            if(conflict){conflicts.put(new JSONObject().put("field",field).put("candidates",versions));if(Arrays.asList("removed","deleted","paused").contains(field)){for(int i=0;i<versions.length();i++)if(versions.getJSONObject(i).optBoolean("value"))value=true;}else if(field.equals("localDone")||field.equals("done"))value=false;else if(field.equals("manualDue")){Long earliest=null;for(int i=0;i<versions.length();i++){Object v=versions.getJSONObject(i).opt("value");if(v instanceof Number&&(earliest==null||((Number)v).longValue()<earliest))earliest=((Number)v).longValue();}value=earliest==null?JSONObject.NULL:earliest;}}
            if(!field.equals("baseline"))result.put(field,value);
        }
        if(records.size()>1){JSONObject first=records.get(0).getJSONObject("data");for(int i=1;i<records.size();i++)if(!records.get(i).optBoolean("historical")){JSONObject other=records.get(i).getJSONObject("data");if(!Objects.equals(first.optString("status"),other.optString("status"))||!Objects.equals(first.opt("due"),other.opt("due"))){result.put("sourceConflict",true);break;}}}
        if(result.optBoolean("sourceConflict")){if(!result.optString("status").equals("pending"))result.put("status","unknown");Long earliest=null;for(JSONObject record:records){Object d=record.getJSONObject("data").opt("due");if(!record.optBoolean("historical")&&d instanceof Number&&(earliest==null||((Number)d).longValue()<earliest))earliest=((Number)d).longValue();}if(earliest!=null)result.put("due",earliest);}
        JSONObject versions=new JSONObject();fields=heads.keys();while(fields.hasNext()){String f=fields.next();JSONArray ids=new JSONArray(),values=heads.getJSONArray(f);for(int i=0;i<values.length();i++)ids.put(values.getJSONObject(i).getString("op"));versions.put(f,ids);}
        result.put("id",e.getString("id")).put("fieldVersions",versions).put("conflicts",conflicts);return result;
    }
    public JSONArray list(String... types) throws JSONException {Set<String> accepted=new HashSet<>(Arrays.asList(types));JSONArray out=new JSONArray();JSONObject entities=data.getJSONObject("entities");Iterator<String> keys=entities.keys();while(keys.hasNext()){JSONObject e=entities.getJSONObject(keys.next());if(accepted.contains(e.getString("type")))out.put(project(e));}return out;}
    public String create(JSONObject fields,boolean host) throws JSONException {String key=UUID.randomUUID().toString();JSONObject v=new JSONObject(fields.toString());if(!v.has("title"))throw new IllegalArgumentException("请填写任务标题");edit(key,"manual",v,host);return key;}
    public JSONArray observations() throws JSONException {JSONArray out=new JSONArray();JSONObject entities=data.getJSONObject("entities");Iterator<String> keys=entities.keys();while(keys.hasNext()){JSONObject e=entities.getJSONObject(keys.next());if(e.optBoolean("dirty"))out.put(new JSONObject().put("entity",e.getString("id")).put("type",e.getString("type")).put("observation",e.getJSONObject("observations").getJSONObject(device())));}return out;}
    public void acceptObservation(JSONObject incoming,String sender) throws JSONException {
        JSONObject e=entity(incoming.getString("entity"),incoming.getString("type")),ob=incoming.getJSONObject("observation"),record=ob.getJSONObject("data");
        if(!Arrays.asList("course","platform").contains(e.getString("type")))throw new IllegalArgumentException("来源对象类型无效");
        String platform=record.optString("platform"),account=record.optString("sourceAccount"),source=record.optString("sourceCourseKey",record.optString("sourceKey"));
        if(!Arrays.asList("mooc","chaoxing").contains(platform)||account.isEmpty()||source.isEmpty())throw new IllegalArgumentException("来源身份不完整");
        String expected=identity(id(),platform,account,source,e.getString("type").equals("course")?"course":record.getString("sourceId"),e.getString("type").equals("course")?"":record.optString("phase"));
        if(!expected.equals(e.getString("id")))throw new IllegalArgumentException("来源身份不匹配");
        JSONObject clean=new JSONObject();for(String k:SOURCE)if(record.has(k))clean.put(k,record.get(k));for(String k:new String[]{"id","key","sourceAccount","sourceCourseKey","sourceKey","courseKey","termId","schoolCourseId","courseId","classId","cpi","url","examOnly"})if(record.has(k))clean.put(k,record.get(k));
        e.getJSONObject("observations").put(sender,new JSONObject().put("historical",ob.optBoolean("historical")).put("data",clean));bump(e,true);
    }
    public JSONObject snapshot(long after) throws JSONException {JSONArray changed=new JSONArray();JSONObject entities=data.getJSONObject("entities");Iterator<String> keys=entities.keys();while(keys.hasNext()){JSONObject e=entities.getJSONObject(keys.next());if(after<0||e.optLong("revision")>after)changed.put(e);}return new JSONObject().put("protocol",PROTOCOL).put("workspace",id()).put("epoch",epoch()).put("revision",revision()).put("entities",changed).put("receipts",data.getJSONObject("receipts"));}
    public void receive(JSONObject snapshot) throws JSONException {
        if(!snapshot.getString("workspace").equals(id())||snapshot.getInt("protocol")!=PROTOCOL)throw new IllegalArgumentException("同步工作区不匹配");
        String previous=data.optString("remoteEpoch");if(!previous.isEmpty()&&!previous.equals(snapshot.getString("epoch")))throw new IllegalArgumentException("主机已恢复备份，请重新核对并配对");
        JSONArray pending=pending(),entities=snapshot.getJSONArray("entities");JSONObject current=data.getJSONObject("entities");
        for(int i=0;i<entities.length();i++){JSONObject remote=new JSONObject(entities.getJSONObject(i).toString()),local=current.optJSONObject(remote.getString("id"));if(local!=null&&local.optBoolean("dirty")){remote.getJSONObject("observations").put(device(),local.getJSONObject("observations").getJSONObject(device()));remote.put("dirty",true);}current.put(remote.getString("id"),remote);}
        JSONObject remoteReceipts=snapshot.getJSONObject("receipts"),all=data.getJSONObject("receipts");Iterator<String> ids=remoteReceipts.keys();while(ids.hasNext()){String key=ids.next();all.put(key,remoteReceipts.get(key));}
        Set<String> pendingIds=new HashSet<>();for(int i=0;i<pending.length();i++)if(!remoteReceipts.has(pending.getJSONObject(i).getString("id")))pendingIds.add(pending.getJSONObject(i).getString("id"));
        Iterator<String> currentIds=current.keys();while(currentIds.hasNext()){JSONObject heads=current.getJSONObject(currentIds.next()).getJSONObject("heads");Iterator<String> names=heads.keys();while(names.hasNext()){String name=names.next();JSONArray versions=heads.getJSONArray(name),keptHeads=new JSONArray();for(int i=0;i<versions.length();i++)if(!pendingIds.contains(versions.getJSONObject(i).getString("op")))keptHeads.put(versions.get(i));heads.put(name,keptHeads);}}
        JSONArray kept=new JSONArray();for(int i=0;i<pending.length();i++){JSONObject op=pending.getJSONObject(i);if(!remoteReceipts.has(op.getString("id"))){all.remove(op.getString("id"));apply(op,false);kept.put(op);}}
        data.put("outbox",kept).put("cursor",snapshot.getLong("revision")).put("remoteEpoch",snapshot.getString("epoch"));
    }
    public void observationsAcknowledged(JSONArray sent) throws JSONException {for(int i=0;i<sent.length();i++){JSONObject x=sent.getJSONObject(i),e=data.getJSONObject("entities").optJSONObject(x.getString("entity"));if(e!=null&&e.getJSONObject("observations").optJSONObject(device()).toString().equals(x.getJSONObject("observation").toString()))e.remove("dirty");}}
    public void join(String workspace) throws JSONException {
        if(id().equals(workspace))return;if(!workspace.matches("[a-fA-F0-9-]{36}"))throw new IllegalArgumentException("工作区身份无效");
        JSONObject old=data.getJSONObject("entities"),mapped=new JSONObject(),aliases=data.getJSONObject("aliases"),mapping=new JSONObject();Iterator<String> keys=old.keys();
        while(keys.hasNext()){String key=keys.next();JSONObject e=old.getJSONObject(key);String next=key;if(Arrays.asList("course","platform").contains(e.getString("type"))){JSONObject r=project(e);next=identity(workspace,r.getString("platform"),r.getString("sourceAccount"),r.optString("sourceCourseKey",r.optString("sourceKey")),e.getString("type").equals("course")?"course":r.getString("sourceId"),e.getString("type").equals("course")?"":r.optString("phase"));}mapping.put(key,next);aliases.put(key,next);}
        keys=old.keys();while(keys.hasNext()){String key=keys.next();JSONObject e=old.getJSONObject(key);e.put("id",mapping.getString(key));JSONObject heads=e.getJSONObject("heads");Iterator<String> fs=heads.keys();while(fs.hasNext()){String f=fs.next();JSONArray versions=heads.getJSONArray(f);for(int i=0;i<versions.length();i++){JSONObject v=versions.getJSONObject(i);v.put("value",remapValue(f,v.get("value"),mapping));}}JSONObject obs=e.getJSONObject("observations");Iterator<String> ds=obs.keys();while(ds.hasNext()){JSONObject r=obs.getJSONObject(ds.next()).getJSONObject("data");r.put("id",e.getString("id"));if(e.getString("type").equals("course"))r.put("key",e.getString("id"));if(r.has("courseKey"))r.put("courseKey",mapping.optString(r.getString("courseKey"),r.getString("courseKey")));}e.put("dirty",obs.has(device()));mapped.put(e.getString("id"),e);}
        JSONArray queue=data.getJSONArray("outbox");for(int i=0;i<queue.length();i++){JSONObject op=queue.getJSONObject(i);op.put("workspace",workspace).put("entity",mapping.optString(op.getString("entity"),op.getString("entity")));JSONObject fs=op.getJSONObject("fields");Iterator<String> fields=fs.keys();while(fields.hasNext()){String f=fields.next();JSONObject patch=fs.getJSONObject(f);patch.put("value",remapValue(f,patch.get("value"),mapping));}data.getJSONObject("receipts").getJSONObject(op.getString("id")).put("digest",Core.hash(canonicalJson(op)));}
        List<String> aliasKeys=new ArrayList<>();keys=aliases.keys();while(keys.hasNext())aliasKeys.add(keys.next());for(String key:aliasKeys)aliases.put(key,mapping.optString(aliases.getString(key),aliases.getString(key)));
        data.put("workspace",workspace).put("entities",mapped).put("cursor",0);data.remove("remoteEpoch");
    }
    private static Object remapValue(String field,Object value,JSONObject mapping) throws JSONException {
        if(Arrays.asList("parent","courseKey").contains(field)&&value instanceof String)return mapping.optString((String)value,(String)value);
        if(Arrays.asList("baseline","template").contains(field)&&value instanceof JSONObject){JSONObject copy=new JSONObject(value.toString());if(copy.has("courseKey"))copy.put("courseKey",mapping.optString(copy.getString("courseKey"),copy.getString("courseKey")));return copy;}return value;
    }
    public void importArchive(JSONObject archive,boolean host) throws JSONException {
        if(!archive.optString("format").equals("keqi-data-v1"))throw new IllegalArgumentException("不是课期任务备份");JSONObject snapshot=archive.getJSONObject("snapshot");if(snapshot.getInt("protocol")!=PROTOCOL)throw new IllegalArgumentException("备份版本不兼容");JSONArray entries=snapshot.getJSONArray("entities");if(entries.length()>10000)throw new IllegalArgumentException("备份数量过多");JSONObject entities=new JSONObject();for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);entities.put(e.getString("id"),e);}
        Workspace imported=new Workspace(new JSONObject().put("workspace",snapshot.getString("workspace")).put("entities",entities).put("receipts",snapshot.getJSONObject("receipts")));imported.join(id());JSONObject records=imported.data.getJSONObject("entities");Iterator<String> keys=records.keys();
        while(keys.hasNext()){String key=keys.next();JSONObject e=records.getJSONObject(key);String type=e.getString("type");if(Arrays.asList("platform","course").contains(type)){JSONObject observations=e.getJSONObject("observations");Iterator<String> ds=observations.keys();while(ds.hasNext()){JSONObject observation=new JSONObject(observations.getJSONObject(ds.next()).toString());observation.put("historical",true);acceptObservation(new JSONObject().put("entity",key).put("type",type).put("observation",observation),"backup:"+Core.hash(canonicalJson(observation)));}}
            JSONObject heads=e.getJSONObject("heads");Iterator<String> fields=heads.keys();while(fields.hasNext()){String field=fields.next();JSONArray versions=heads.getJSONArray(field);for(int i=0;i<versions.length();i++){Object value=versions.getJSONObject(i).get("value");JSONObject existing=data.getJSONObject("entities").optJSONObject(key);boolean duplicate=false;JSONArray candidates=existing==null?null:existing.getJSONObject("heads").optJSONArray(field);if(candidates!=null)for(int j=0;j<candidates.length();j++)if(canonicalJson(value).equals(canonicalJson(candidates.getJSONObject(j).get("value"))))duplicate=true;if(!duplicate)edit(key,type,new JSONObject().put(field,value),new JSONObject().put(field,new JSONArray()),host);}}
        }
    }
}
