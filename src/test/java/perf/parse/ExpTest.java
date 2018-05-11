package perf.parse;

import org.junit.Ignore;
import org.junit.Test;
import perf.parse.internal.CheatChars;
import perf.parse.internal.JsonBuilder;
import perf.yaup.json.Json;

import static org.junit.Assert.*;

/**
 *
 */
public class ExpTest {


    @Test
    public void pushTarget_named(){
        Exp push = new Exp("push","(?<a>a)").set(Rule.PushTarget,"a");

        JsonBuilder b = new JsonBuilder();
        push.apply(new CheatChars("a"),b,null);

        System.out.println(b.debug(true));
        assertEquals("target name","a",b.getContextString(JsonBuilder.NAME_KEY,false));
    }
    @Test
    public void prePopTarget_named(){
        Exp a = new Exp("a","(?<a>a)").set(Rule.PrePopTarget,"popMe");

        JsonBuilder b = new JsonBuilder();
        b.getTarget().set("name",0);
        b.pushTarget(new Json(),"popMe");
        b.getTarget().set("name",1);
        b.pushTarget(new Json());
        b.getTarget().set("name",2);
        a.apply(new CheatChars("a"),b,null);

        assertFalse("no named target",b.hasContext(JsonBuilder.NAME_KEY,true));
    }

    @Test
    public void rootTarget(){
        Exp group = new Exp("group","(?<a>a)").group("one").group("two");
        Exp root = new Exp("root","(?<b>b)").set(Rule.TargetRoot);
        Exp close = new Exp("close","c").set(Merge.PreClose);
        Parser p = new Parser();
        p.add(group);
        p.add(root);
        p.add(close);

        Json json = p.onLine("abc");
        assertTrue("json.b",json.has("b"));
    }
    @Test
    public void rootTarget_group(){
        Exp group = new Exp("group","(?<a>a)").group("one").group("two");
        Exp root = new Exp("root","(?<b>b)").group("uno").group("dos").set(Rule.TargetRoot);
        Exp close = new Exp("close","c").set(Merge.PreClose);
        Parser p = new Parser();
        p.add(group);
        p.add(root);
        p.add(close);

        Json json = p.onLine("abc");
        assertFalse("json.b",json.has("b"));
        assertTrue("json.uno",json.has("uno"));
        System.out.println(json.toString(2));

    }


    @Test
    public void keySplit(){
        Exp exp = new Exp("keySplit","(?<foo.bar>.*)");

        Json result = exp.apply("biz");

        assertTrue(result.has("foo"));
        assertTrue(result.getJson("foo").has("bar"));
        assertEquals("biz",result.getJson("foo").getString("bar"));
    }

    @Test
    public void valueFrom(){
        assertEquals("Default value should be Key", Value.Key, Value.from("fooooooo"));
    }
    @Test
    public void eatFrom(){
        assertEquals("default Eat should be Width", Eat.Width, Eat.from(1));
        assertEquals("default Eat should be Width", Eat.Width, Eat.from(10));
        assertEquals("Failed to identify Eat.None", Eat.None, Eat.from(Eat.None.getId()));
    }
    @Test
    public void parseKMG(){

        assertEquals("wrong value for 1", Math.pow(1024.0,0), Exp.parseKMG("1"), 0.000);
        assertEquals("wrong value for 8b (expected 1 byte)",Math.pow(1024.0,0), Exp.parseKMG("8b"),0.000);
        assertEquals("wrong value for 1k",Math.pow(1024.0,1), Exp.parseKMG("1k"),0.000);
        assertEquals("wrong value for 1m",Math.pow(1024.0,2), Exp.parseKMG("1m"),0.000);
        assertEquals("wrong value for 1g",Math.pow(1024.0,3), Exp.parseKMG("1g"),0.000);
        assertEquals("wrong value for 1t",Math.pow(1024.0,4), Exp.parseKMG("1t"),0.000);

    }

    @Test
    public void groupOrder(){
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("kv","(?<key>\\w+)=(?<value>\\w+)").group("first").group("second");
        p.apply(new CheatChars("foo=bar"),b,null);
        assertTrue("Groupings should be FiFo starting at root", b.getRoot().has("first"));
        assertTrue("Groupings should be FiFo starting at root", b.getRoot().getJson("first").has("second"));

    }
    @Test
    public void valueInPattern(){
        Exp p = new Exp("valueInpattern","(?<id:targetId>\\w+) (?<nest:nestLength>\\w+) (?<key:key>\\w+)(?<size:kmg>\\w+) ");
        assertEquals("id should use targetId value",Value.TargetId,p.get("id"));
        assertEquals("nest should use key value",Value.NestLength,p.get("nest"));
        assertEquals("key should use key value",Value.Key,p.get("key"));

        assertEquals("size should use kmg value",Value.KMG,p.get("size"));
    }

    @Test
    public void key(){
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("kv","(?<key>\\w+)=(?<value>\\w+)")
                .key("key");
        p.apply(new CheatChars("foo=bar"),b,null);
        assertTrue("Should be grouped by value of key field (foo)", b.getRoot().has("foo"));

    }
    @Test
    public void extend(){
        JsonBuilder b = new JsonBuilder();
        Json status = new Json(false);
        b.getRoot().set("status",status);

        Exp p = new Exp("kv","(?<key>\\w+)=(?<value>\\w+)").extend("status");
        p.apply(new CheatChars("foo=bar"),b,null);
        p.apply(new CheatChars("fizz=fuzz"),b,null);

        assertTrue("Status should have value and keyas child objects but is "+b.getRoot(), status.has("value") && status.has("key"));
    }


    @Test
    public void extend_group(){
        JsonBuilder b = new JsonBuilder();
        Json status = new Json(false);
        b.getRoot().set("status",status);

        Exp p = new Exp("kv","(?<key>\\w+)=(?<value>\\w+)").extend("status").group("lock").set(Merge.Entry);
        p.apply(new CheatChars("foo=bar"),b,null);
        p.apply(new CheatChars("fizz=fuzz"),b,null);


        assertTrue("Status should have <lock> as child object but is "+b.getRoot(), status.has("lock"));
    }

    @Test
    public void nest_entry(){

        Exp norm = new Exp("kv","\\s*(?<key>\\S+)\\s*:\\s*(?<value>.*)")
            .set(Merge.Entry)
            .eat(Eat.Match);
        Exp nest = new Exp("nest","(?<child:nestLength>[\\s-]*-\\s*)")
            .eat(Eat.Match)
            .add(norm);

        Parser p = new Parser();
        p.add(nest);
        p.add(norm);

        p.onLine("foo:bar");
        p.onLine("  - a : Alpha");
        p.onLine("    b : Bravo");
        p.onLine("  - y : Yankee");
        p.onLine("    z : Zulu");

        Json root = p.getBuilder().getRoot();

        Json expected = Json.fromString("{\"key\":\"foo\",\"value\":\"bar\",\"child\":[[{\"key\":\"a\",\"value\":\"Alpha\"},{\"key\":\"b\",\"value\":\"Bravo\"}],[{\"key\":\"y\",\"value\":\"Yankee\"},{\"key\":\"z\",\"value\":\"Zulu\"}]]}\n");
        assertEquals("root should have 2 children each with 2 entries",expected,root);
    }

    @Test
    public void value_nestLength(){

        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("tree","(?<nest>\\s*)(?<name>\\w+)");
        p.set("nest", Value.NestLength);
        //b.getRoot().add("name","root");

        p.apply(new CheatChars("a"),b,null);
        p.apply(new CheatChars(" aa"),b,null);
        p.apply(new CheatChars("  aaa"),b,null);
        p.apply(new CheatChars("b"),b,null);

        assertTrue("tree should use nest as the child key:\n"+b.getRoot().toString(2),b.getRoot().has("nest") && b.getRoot().getJson("nest").isArray());
        assertTrue("expect only one key on root json:\n"+b.getRoot().toString(2),b.getRoot().size()==1);
        assertTrue("expect nest to have 2 children:\n"+b.getRoot().getJson("nest").toString(2),b.getRoot().getJson("nest").size()==2);
        assertTrue("expect a to have one child",b.getRoot().getJson("nest").getJson(0).getJson("nest").isArray() && b.getRoot().getJson("nest").getJson(0).getJson("nest").size()==1);
        assertTrue("expect a.aa to have one child",b.getRoot().getJson("nest").getJson(0).getJson("nest").getJson(0).getJson("nest").isArray() && b.getRoot().getJson("nest").getJson(0).getJson("nest").getJson(0).getJson("nest").size()==1);

    }
    @Test
    public void value_nestLength_multi(){

        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("tree","(?<nest>\\s*)(?<name>\\w+)");
        p.set("nest", Value.NestLength);
        //b.getRoot().add("name","root");

        p.apply(new CheatChars("a"),b,null);
        p.apply(new CheatChars("  aa"),b,null);
        p.apply(new CheatChars("    aaa"),b,null);
        p.apply(new CheatChars("    aab"),b,null);
        p.apply(new CheatChars("      aaba"),b,null);
        p.apply(new CheatChars("    aac"),b,null);
        p.apply(new CheatChars("b"),b,null);

        System.out.println(b.getRoot().toString(2));

        Json root = b.getRoot();
        assertTrue("root has nest:",root.has("nest") && root.get("nest") instanceof Json);
        Json nest = root.getJson("nest");
        assertEquals("nest has 2 children",2,nest.size());
        assertEquals("nest[0].name","a",nest.getJson(0).getString("name"));
        assertEquals("nest[0].name","b",nest.getJson(1).getString("name"));

    }


    @Test
    public void valueTargetId(){
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("tid","(?<id>\\d+) (?<name>\\S+)");
        p.set("id",Value.TargetId);
        p.apply(new CheatChars("1 foo"),b,null);
        p.apply(new CheatChars("1 bar"),b,null);

        assertTrue("root should have a name entry but was: "+b.getRoot(),b.getRoot().has("name"));
        assertTrue("root should have two name values but was: "+b.getRoot(),b.getRoot().getJson("name").size()==2);
        p.apply(new CheatChars("2 biz"),b,null);
        p.apply(new CheatChars("2 fiz"),b,null);
        assertTrue("root should have a name entry but was: "+b.getRoot(),b.getRoot().has("name"));
        assertTrue("root should have two name values but was: "+b.getRoot(),b.getRoot().getJson("name").size()==2);

    }
    @Test
    public void autoNumber(){
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("kv","(?<key>\\w+)=(?<value>\\w+)");
        p.apply(new CheatChars("age=23"),b,null);

        assertEquals(23,b.getRoot().getLong("value"));
    }
    @Test
    public void valueKMG(){
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("kv","(?<key>\\w+)=(?<value>\\w+)").set("value", Value.KMG);
        p.apply(new CheatChars("age=1G"),b,null);
        assertEquals(Math.pow(1024.0,3),b.getRoot().getLong("value"),0.000);
    }

    @Test
    public void valueCount(){
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("kv","(?<key>\\w+)=(?<value>\\w+)").set("value", Value.Count);
        p.apply(new CheatChars("age=old"),b,null);
        assertEquals(1,b.getRoot().getLong("old"));
        p.apply(new CheatChars("age=old"),b,null);
        assertEquals(2,b.getRoot().getLong("old"));
    }
    @Test
    public void valueSum(){
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("kv","(?<key>\\w+)=(?<value>\\w+)").set("value", Value.Sum);
        p.apply(new CheatChars("age=23"),b,null);
        assertEquals(23,b.getRoot().getLong("value"));
        p.apply(new CheatChars("age=23"),b,null);
        assertEquals(46,b.getRoot().getLong("value"));
    }
    @Test
    public void valueKey(){
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("kv","(?<key>\\w+)=(?<value>\\w+)").set("key", "value");
        p.apply(new CheatChars("age=23"),b,null);

        assertTrue("Should turn value of <key> to the key for <value>", b.getRoot().has("age"));
        assertEquals("expected {\"age\":\"23\"}","23",b.getRoot().getString("age"));
    }
    @Test
    public void valueBoolanKey(){
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("kv","(?<key>\\w+)=(?<value>\\w+)").set("key", Value.BooleanKey);
        p.apply(new CheatChars("age=23"),b,null);
        assertEquals("value should be a boolean",true,b.getRoot().getBoolean("key"));
    }
    @Test
    public void valueBoolanValue(){
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("kv","(?<key>\\w+)=(?<value>\\w+)").set("key", Value.BooleanValue);
        p.apply(new CheatChars("age=23"),b,null);
        assertEquals("value should be a boolean",true,b.getRoot().getBoolean("age"));
    }
    @Test
    public void valuePosition(){
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("kv","(?<key>\\w+)=(?<value>\\w+)").set("key", Value.Position);
        p.apply(new CheatChars("012345 age=23"),b,null);
        assertEquals("should equal the offset from start of line", 7, b.getRoot().getLong("key"));
    }
    @Test
    public void valueString(){
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("kv","(?<key>\\w+)=(?<value>\\w+)").set("value", Value.String);
        p.apply(new CheatChars("age=23"),b,null);
        p.apply(new CheatChars("age=23"),b,null);

        assertEquals("<value> should use string concat rather than list append", "2323", b.getRoot().getString("value"));
    }
    @Test
    public void valueList(){
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("kv","(?<key>\\w+)=(?<value>\\w+)").set("value", Value.List);
        p.apply(new CheatChars("age=23"),b,null);

        p.apply(new CheatChars("age=23"),b,null);

        assertTrue("<key> should be treated as a list by default",b.getRoot().get("key") instanceof Json);
        assertTrue("<value> should be treated as a list",b.getRoot().get("value") instanceof Json);
    }

    @Test
    public void eatMatch(){
        CheatChars line = new CheatChars("age=23, age=24, age=26");
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("kv","(?<key>\\w+)=(?<value>\\w+)").eat(Eat.Match);
        p.apply(line, b, null);
        assertEquals("should remove the matched string", ", age=24, age=26", line.toString());
    }
    @Test
    public void eatToMatch(){
        CheatChars line = new CheatChars("foo=1 bar=1 foo=2");
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("bar","bar=(?<bar>\\S+)").eat(Eat.ToMatch);
        p.apply(line,b,null);
        assertEquals("should remove first foo"," foo=2",line.toString());
    }


    @Test
    public void eatWidth(){
        CheatChars line = new CheatChars("age=23, age=24, age=26");
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("kv","(?<key>\\w+)=(?<value>\\w+)").eat(8);
        p.apply(line,b,null);
        assertEquals("should remove the matched string","age=24, age=26",line.toString());
    }
    @Test
    public void eatLine(){
        CheatChars line = new CheatChars("age=23, age=24, age=26");
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("kv","(?<key>\\w+)=(?<value>\\w+)").eat(Eat.Line);
        p.apply(line,b,null);
        assertEquals("should remove the matched string","",line.toString());
    }

    @Test
    public void lineStart(){
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("kv", "(?<key>\\w+)=(?<value>\\w+)")
                .add( new Exp("-","(?<sk>\\w+)\\-(?<sv>\\w+)").set(Rule.LineStart) );

        p.apply(new CheatChars("a-b key=value foo-bar"),b,null);

        assertEquals("should match first <sk>-<sv> not the last","a",b.getRoot().getString("sk"));
    }

    @Test @Ignore
    public void nestmap(){
        JsonBuilder b = new JsonBuilder();
        Exp open = new Exp("start","^\\s*\\{")
            .group("child").set(Rule.PushTarget).set(Merge.Entry).eat(Eat.Match);
        Exp comma = new Exp("comma","^\\s*,\\s*").eat(Eat.Match);
        Exp kvSeparator = new Exp("kvSeparator","^\\s*:\\s*").eat(Eat.Match);
        Exp key = new Exp("key","^\\s*(?<key>[^:\\s,\\]]+)\\s*").eat(Eat.Match).set(Merge.Entry);;
        Exp value = new Exp("value","^\\s*(?<value>[^,\\}\\{]*[^\\s,\\}\\{])").eat(Eat.Match);
        Exp close = new Exp("close","^\\s*\\}")
                .eat(Eat.Match)
                .set(Rule.PostPopTarget)
                .set(Rule.PostPopTarget)
                ;
        Parser p = new Parser();
        p.add(open.clone().set(Rule.RepeatChildren)
                .add(comma)
                .add(open.clone().set(Rule.Repeat))
                .add(close.set(Rule.Repeat))
                .add(key
                    .add(kvSeparator
                        .add(value)
                    )
                )
        );


        //p.onLine("{ a : Alpha, b : Bravo, c : { c.a : Able, c.b : Ben }, d : Dan}");

        p.onLine("{ a : Alpha, b : Bravo {b.a: Able, b.b: Ben }, c : Charlie }");
        Json root = p.getBuilder().getRoot();



    }

    @Test @Ignore
    public void nestLists(){

        //works with a double pop
        JsonBuilder b = new JsonBuilder();
        Exp open = new Exp("start","^\\s*\\[")
                .group("child").set(Rule.PushTarget).set(Merge.Entry).eat(Eat.Match);
        Exp comma = new Exp("comma","^\\s*,\\s*").eat(Eat.Match);
        Exp entry = new Exp("entry","^\\s*(?<key>[^:\\s,\\]]+)\\s*").eat(Eat.Match).set(Merge.Entry);
        Exp close = new Exp("close","^\\s*]")
                .eat(Eat.Match)
                .set(Rule.PostPopTarget)
                .set(Rule.PostPopTarget);

        Parser p = new Parser();
        p.add(open.clone().set(Rule.RepeatChildren)
                .add(comma)
                .add(open.clone())
                .add(close)
                .add(entry)
        );

        p.onLine("[ alpha, bravo, [b.b b.c], charlie, [ yankee, zulu ] delta ]");

        Json root = p.getBuilder().getRoot();


    }

    @Test
    public void repeat(){
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("num","(?<num>\\d+)").set(Rule.Repeat);
        p.apply(new CheatChars("1 2 3 4"),b,null);
        assertEquals("num should be an array with 4 elements",4,b.getRoot().getJson("num").size());
    }
    @Test
    public void pushTarget(){
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("num","(?<num>\\d+)").group("pushed").set(Rule.PushTarget).set(Merge.Entry);
        p.apply(new CheatChars(" 1 "), b, null);
        p.apply(new CheatChars(" 2 "), b, null);
        p.apply(new CheatChars(" 3 "), b, null);

        assertTrue("context should not equal root",b.getRoot()!=b.getTarget());
    }
    @Test
    public void postPopTarget(){
        JsonBuilder b = new JsonBuilder();
        Json lost = new Json();
        lost.set("lost","lost");
        b.pushTarget(lost);
        Exp p = new Exp("num","(?<num>\\d+)").group("pushed").set(Rule.PostPopTarget);
        p.apply(new CheatChars(" 1 "),b,null);

        assertTrue("context should not equal starting context", lost != b.getTarget());
        assertTrue("fields should be applied to context before pop",lost.has("pushed"));
    }
    @Test
    public void prePopTarget(){
        JsonBuilder b = new JsonBuilder();
        Json lost = new Json();
        lost.set("lost","lost");
        b.pushTarget(lost);
        Exp p = new Exp("num","(?<num>\\d+)").group("pushed").set(Rule.PrePopTarget);
        p.apply(new CheatChars(" 1 "), b, null);

        assertTrue("context should not equal starting context",lost!=b.getTarget());
        assertTrue("fields should be applied to context before pop", b.getTarget().has("pushed"));
    }
    @Test
    public void postClearTarget(){
        JsonBuilder b = new JsonBuilder();
        Json first = new Json();
        first.set("ctx", "lost");
        Json second = new Json();
        second.set("ctx","second");
        b.pushTarget(first);
        b.pushTarget(second);
        Exp p = new Exp("num","(?<num>\\d+)").group("pushed").set(Rule.PostClearTarget);
        p.apply(new CheatChars(" 1 "),b,null);
        assertTrue("context should not equal starting context",b.getRoot()==b.getTarget());
        assertTrue("fields should be applied to context before pop", second.has("pushed"));
    }

    @Test
    public void newStart(){
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("num","(?<num>\\d+)").set(Merge.PreClose);
        p.apply(new CheatChars(" 1 "),b,null);
        p.apply(new CheatChars(" 2 "), b, null);

        assertEquals("matches should not be combined", 2, b.getRoot().getLong("num"));
        assertTrue("previous match should be moved to a closed root",b.wasClosed());
    }

    // I don't see a difference between extend and group
    @Test
    public void extend_entry(){
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("num","(?<num>\\d+)").extend("nums").set(Merge.Entry);
        p.apply(new CheatChars(" 1 "),b,null);
        p.apply(new CheatChars(" 2 "), b, null);

        assertEquals("nums should have 2 entries",2,b.getRoot().getJson("nums").size());
    }
    @Test
    public void group_entry(){
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("num","(?<num>\\d+)").group("nums").set(Merge.Entry);
        p.apply(new CheatChars(" 1 "),b,null);
        p.apply(new CheatChars(" 2 "), b, null);

        assertEquals("nums should have 2 entries",2,b.getRoot().getJson("nums").size());

    }
    @Test
    public void group_extend(){
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("kv", "(?<key>\\w+)=(?<value>\\w+)").set("key","value").group("kv").set(Merge.Extend);
        p.apply(new CheatChars(" age=23 "),b,null);
        p.apply(new CheatChars(" size=small "),b,null);
        assertEquals("<kv> should be and array of length 1",1,b.getRoot().getJson("kv").size());

    }
    @Test
    public void collection(){
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("kv", "(?<key>\\w+)=(?<value>\\w+)").set("key","value").group("kv").set(Merge.Collection);
        p.apply(new CheatChars(" age=23 "),b,null);
        p.apply(new CheatChars(" size=small "),b,null);

        assertTrue("<kv> should have kv.size",b.getRoot().getJson("kv").has("size"));
        assertTrue("<kv> should have kv.age",b.getRoot().getJson("kv").has("age"));
    }


    @Test
    public void childOrder(){
        JsonBuilder b = new JsonBuilder();
        Exp p = new Exp("start","\\[").eat(Eat.Match)
                .add(new Exp("quoted","^\\s*,?\\s*\"(?<value>[^\"]+)\"")
                    .group("child").set(Merge.Entry).eat(Eat.Match)
                )
                .add(new Exp("normal","^\\s*,?\\s*(?<value>[^,\\]]*[^\\s,\\]])")
                    .group("child").set(Merge.Entry).eat(Eat.Match)
                )
                .set(Rule.RepeatChildren);

        p.apply(new CheatChars("[ aa, \"bb,bb]bb\" ,cc]"),b,null);

        Json json = b.getRoot();

        assertTrue("match should contain a child entry",json.has("child"));
        assertEquals("child should contain 3 entires",3,json.getJson("child").size());

    }

}