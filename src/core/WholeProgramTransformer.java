package core;

import java.util.*;
import java.util.Map.Entry;

import soot.*;
import soot.jimple.*;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;

public class WholeProgramTransformer extends SceneTransformer {

	private TreeMap<Integer, String> queries;

	private int allocId;

	private Anderson anderson;

	private int newNum = 1;

	private boolean flag = false;

	@Override
	protected void internalTransform(String arg0, Map<String, String> arg1) {
		
		queries = new TreeMap<Integer, String>();
		anderson = new Anderson();

		SootMethod mainMethod = Scene.v().getMainMethod();
		newNum++;
		OneMethod(mainMethod, mainMethod.getSignature(),Context.of());

		
		anderson.run();
		String answer = "";
		for (Entry<Integer, String> q : queries.entrySet()) {
			TreeSet<Integer> result = anderson.getPointsToSet(q.getValue());
			answer += q.getKey().toString() + ":";
			if(result == null)
				answer += " " + q.getKey().toString();
			else {
				for (Integer i : result) {
					answer += " " + i;
				}
			}
			answer += "\n";
		}
		AnswerPrinter.printAnswer(answer);
		
	}

	private void OneMethod(SootMethod method , String methodSignature, Context c)
	{
		Body body = method.getActiveBody();
		UnitGraph graph = new BriefUnitGraph(body);
		Unit head = graph.getHeads().iterator().next();
		OneBranch(head, graph, null, methodSignature,c);
	}

	private void OneBranch(Unit u, UnitGraph graph, String Signature, String methodSignature, Context c )
	{
		while (true) {
			//System.out.println(u);
			OneStat(u, methodSignature,c);
			List<Unit> succs = graph.getSuccsOf(u);
			int SuccsNum = succs.size();
			if (SuccsNum == 1) { // 无分支继续向下运行
				u = succs.iterator().next();
			} else if (SuccsNum > 1) { //有多个后继节点说明有分支
				for (Unit succ : succs) {
					String branchSignature = succ.toString();
					if (branchSignature.equals(Signature)) {
						continue;
					}
					Context tmpC = new Context(c.args,c.depth,c.Num, c.fatherNum,c.lastVar);
					tmpC.varMap = c.varMap;
					OneBranch(succ, graph, succ.toString(), methodSignature ,tmpC);
				}
				return;
			} else {
				return;
			}
		}
	}

	private void OneStat(Unit u, String methodSignature, Context c)
	{
		if (u instanceof InvokeStmt) {
			InvokeExpr ie = ((InvokeStmt) u).getInvokeExpr();
			if (ie.getMethod().getSignature().equals(methodSignature)){
				return; //此处递归不处理
			}
			else if (ie.getMethod().getSignature().equals("<java.lang.Object: void <init>()>")){
				return;
			}
			else if (ie.getMethod().toString().equals("<benchmark.internal.Benchmark: void alloc(int)>")) {
				allocId = ((IntConstant)ie.getArgs().get(0)).value;
				flag = true;
			}
			else if (ie.getMethod().toString().equals("<benchmark.internal.Benchmark: void test(int,java.lang.Object)>")) {
				Value v = ie.getArgs().get(1);
				int id = ((IntConstant)ie.getArgs().get(0)).value;
				queries.put(id, v.toString()+'_'+c.Num);
			}
			else{
				List<Value> invokeArgs = ie.getArgs();
				Context tmpC = new Context(invokeArgs,c.depth+1,++newNum, c.Num,c.lastVar);
				tmpC.varMap = c.varMap;
				OneMethod(ie.getMethod(),ie.getMethod().getSignature(),tmpC);
			}
		}
		if (u instanceof IdentityStmt){
			Value Lop = ((DefinitionStmt) u).getLeftOp();
			Value Rop =  ((DefinitionStmt) u).getRightOp();
			if (Rop instanceof ParameterRef) {
				ParameterRef pr = (ParameterRef) Rop;
				int index = pr.getIndex();
				if(index >= 0 && index < c.args.size()) {
					anderson.addAssignConstraint(c.args.get(pr.getIndex()).toString() + '_' + c.fatherNum, Lop.toString() + '_' + c.Num);
					c.varMap.put(Lop.toString() + '_' + c.Num,c.args.get(pr.getIndex()).toString() + '_' + c.fatherNum);
				}
			}
			else if (Rop instanceof ThisRef)
			{
				anderson.addAssignConstraint( c.lastVar,Lop.toString() + '_' + c.Num);
				c.varMap.put(Lop.toString() + '_' + c.Num,c.lastVar);
			}
		}
		if (u instanceof AssignStmt) {
			Value Lop = ((DefinitionStmt) u).getLeftOp();
			Value Rop =  ((DefinitionStmt) u).getRightOp();
			String Ls = null;
			String Rs = null;
			//处理右边
			if (Rop instanceof NewExpr) {
				//System.out.println("Alloc " + allocId);
				if (flag) {
					anderson.addNewConstraint(allocId,  ((DefinitionStmt) u).getLeftOp().toString()+'_'+c.Num);
					c.setLastVar(Lop.toString()+ '_' + c.Num);
					flag = false;
				}
				return;
			}
			else if (Rop instanceof InstanceFieldRef)
			{
				InstanceFieldRef rifref = (InstanceFieldRef) Rop;
				Local rbase = (Local) rifref.getBase();
				Rs = rbase.toString()+'_'+c.Num;
				while(c.varMap.containsKey(Rs))
				{
					Rs = c.varMap.get(Rs);
				}
				Rs = Rs + '_' + rifref.getFieldRef().toString();
			}
			else if (Rop instanceof ArrayRef) {
				ArrayRef rref = (ArrayRef) Rop;
				Local rbase = (Local) rref.getBase();
				Value rindexValue = rref.getIndex();
				if (rindexValue instanceof IntConstant) {
					Rs = rbase.toString()+'_'+c.Num;
					while(c.varMap.containsKey(Rs))
					{
						Rs = c.varMap.get(Rs);
					}
					Rs = Rs + rindexValue.toString();
				}
			}
			else if (Rop instanceof  Local)
			{
				Rs = Rop.toString()+'_'+c.Num;
			}
			//处理左边
			if (Lop instanceof InstanceFieldRef)
			{
				InstanceFieldRef lifref = (InstanceFieldRef) Lop;
				Local lbase = (Local) lifref.getBase();
				Ls = lbase.toString()+'_'+c.Num;
				while(c.varMap.containsKey(Ls))
				{
					Ls = c.varMap.get(Ls);
				}
				Ls = Ls + '_' + lifref.getFieldRef().toString();
			}
			else if (Lop instanceof ArrayRef) {
				ArrayRef lref = (ArrayRef) Lop;
				Local lbase = (Local) lref.getBase();
				Value lindexValue = lref.getIndex();
				if (lindexValue instanceof IntConstant) {
					Ls = lbase.toString()+'_'+c.Num;
					while(c.varMap.containsKey(Ls))
					{
						Ls = c.varMap.get(Ls);
					}
					Ls = Ls + lindexValue.toString();
				}
			}
			else if (Lop instanceof  Local)
			{
				Ls = Lop.toString()+'_'+c.Num;
			}
			if(Ls != null && Rs != null)
			{
				anderson.addAssignConstraint(Rs, Ls);
				c.varMap.put(Ls,Rs);
			}
		}
	}

}
