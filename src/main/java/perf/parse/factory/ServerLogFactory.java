package perf.parse.factory;

import perf.parse.Eat;
import perf.parse.Exp;
import perf.parse.Merge;
import perf.parse.Parser;
import perf.parse.Rule;
import perf.parse.Value;

/**
 * Created by wreicher
 */
public class ServerLogFactory {

    public Parser newParser(){
        Parser p = new Parser();
        addToParser(p);
        return p;
    }
    public void addToParser(Parser p){
        p.add(newStartEntryExp());
        p.add(newFrameExp());
        p.add(newCausedByExp());
        p.add(newStackRemainderExp());
        p.add(newMessageExp());
    }


    public Exp newStartEntryExp(){
        return new Exp("timestamp","(?<timestamp>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3})")
            .eat(Eat.Match)
            .set(Merge.PreClose)
            .add(new Exp("level", " (?<level>[A-Z]+)\\s+")
                    .eat(Eat.Match))
            .add(new Exp("component","\\[(?<component>[^\\]]+)\\]\\s+")
                    .eat(Eat.Match))
            .add(new Exp("threadName","\\((?<threadName>.+?)\\) ")
                    .eat(Eat.Match))
            .add(new Exp("message","(?<message>.+\n?)")
                    .eat(Eat.Match));
    }
    public Exp newFrameExp(){
        return new Exp("frame","\\s+at (?<frame>[^\\(]+)")
            .eat(Eat.Match)
            .group("stack").set(Merge.Entry)
            //.debug()
            .add(new Exp("nativeMethod", "\\((?<nativeMethod>Native Method)\\)")
                    .eat(Eat.Line)
                    .set("nativeMethod", Value.BooleanKey))
            .add(new Exp("lineNumber","\\((?<file>[^:]+):(?<line>[^\\)]+)\\)")
                    .eat(Eat.Line)
                )
            .add(new Exp("unknownSource","\\((?<unknownSource>Unknown Source)\\)")
                    .eat(Eat.Line)
                    .set("unknownSource", Value.BooleanKey)
                );
    }
    public Exp newCausedByExp(){
        return new Exp("causedBy","Caused by: (?<exception>[^:]+): (?<message>.+\n?)")
            //.group("stack")
            .group("causedBy")
            .set(Rule.PushTarget).eat(Eat.Line);
    }
    public Exp newStackRemainderExp(){
        return new Exp("more","\\s+\\.\\.\\. (?<stackRemainder>\\d+) more")

                .eat(Eat.Line);
    }
    public Exp newMessageExp(){
        return new Exp("message","(?<message>.+\n?)").set("message", Value.String);
    }
}