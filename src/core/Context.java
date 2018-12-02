package core;

import soot.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class Context {

    public final List<Value> args;

    public final int depth; //调用深度

    public final int Num;

    public final int fatherNum;

    public String lastVar;

    public HashMap<String, String> varMap = new HashMap<>();


    public static Context of() {
        return new Context( Collections.emptyList(),0,1,0,null );
    }
    public Context(List<Value> args,int depth,int times,int fatherNum,String var)
    {
        this.args = args;
        this.depth = depth;
        this.Num = times;
        this.fatherNum = fatherNum;
        this.lastVar = var;
    }

    public void setLastVar(String var)
    {
        this.lastVar = var;
    }
}
