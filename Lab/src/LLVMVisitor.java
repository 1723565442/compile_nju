import org.antlr.v4.runtime.tree.TerminalNode;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;

import java.util.HashMap;
import java.util.Stack;

import static org.bytedeco.llvm.global.LLVM.*;

public class LLVMVisitor extends SysYParserBaseVisitor<LLVMValueRef> {
    //创建module
    public LLVMModuleRef module = LLVMModuleCreateWithName("module");

    //初始化IRBuilder，后续将使用这个builder去生成LLVM IR
    public LLVMBuilderRef builder = LLVMCreateBuilder();

    //考虑到我们的语言中仅存在int一个基本类型，可以通过下面的语句为LLVM的int型重命名方便以后使用
    public LLVMTypeRef i32Type = LLVMInt32Type();
    public  LLVMTypeRef i1Type = LLVMInt1Type();
    public LLVMTypeRef voidType = LLVMVoidType();

    private final LLVMValueRef zero = LLVMConstInt(i32Type, 0, 0);

    LLVMValueRef function = null;
    private GlobalScope globalScope = null;
    private Scope currentScope = null;
    private int localScopeNum = 0;

    public Stack<LLVMBasicBlockRef> whileCond = new Stack<>();
    public Stack<LLVMBasicBlockRef> whileNext = new Stack<>();

    public LLVMVisitor(){
        //初始化LLVM
        LLVMInitializeCore(LLVMGetGlobalPassRegistry());
        LLVMLinkInMCJIT();
        LLVMInitializeNativeAsmPrinter();
        LLVMInitializeNativeAsmParser();
        LLVMInitializeNativeTarget();
    }

    @Override
    public LLVMValueRef visitTerminal(TerminalNode terminalNode){
        if (terminalNode.getSymbol().getType() == SysYParser.INTEGER_CONST){
            return LLVMConstInt(i32Type, (int) Integer.parseInt(Helper.toDec(terminalNode.getText())), 1);
        }
        return super.visitTerminal(terminalNode);
    }

    @Override
    public LLVMValueRef visitFuncDef(SysYParser.FuncDefContext ctx){
        String name = ctx.IDENT().getText();
        LLVMTypeRef ft1 = ctx.funcType().getText().equals("int") ? i32Type : voidType;
        int parNum = null != ctx.funcFParams()  ? ctx.funcFParams().funcFParam().size() : 0;
        PointerPointer<Pointer> parTys = new PointerPointer<>(parNum);
        for(int i = 0;i<parNum;i++) {
            parTys.put(i, i32Type);
        }

        LLVMTypeRef ft = LLVMFunctionType(ft1, parTys,parNum,0);
        function = LLVMAddFunction(module, name, ft);
        LLVMBasicBlockRef funcEntry = LLVMAppendBasicBlock(function, name + "Entry");
        LLVMPositionBuilderAtEnd(builder, funcEntry);

        currentScope.define(name, function,ft1);
        currentScope = new LocalScope(currentScope);

        for (int i = 0;i < parNum;i++){
            LLVMValueRef parPointer = LLVMBuildAlloca(builder, i32Type,ctx.funcFParams().funcFParam(i).IDENT().getText());
            currentScope.define(ctx.funcFParams().funcFParam(i).IDENT().getText(), parPointer,i32Type);
            LLVMBuildStore(builder, LLVMGetParam(function, i), parPointer);
        }
        super.visitFuncDef(ctx);
        currentScope = currentScope.getEnclosingScope();
        if (ctx.funcType().getText().equals("void")) LLVMBuildRet(builder, null);
        return function;
    }

    @Override
    public LLVMValueRef visitCallFuncExp(SysYParser.CallFuncExpContext ctx){
        LLVMValueRef function = currentScope.resolve(ctx.IDENT().getText());
        int parNum = (null != ctx.funcRParams() ) ? ctx.funcRParams().param().size() : 0;
        PointerPointer<Pointer> pars = new PointerPointer<>(parNum);
        for (int i = 0;i<parNum;i++){
            SysYParser.ExpContext ectx = ctx.funcRParams().param(i).exp();
            pars.put(i, ectx.accept(this));
        }
        if (currentScope.reolveType(ctx.IDENT().getText()).equals(i32Type))
        return LLVMBuildCall(builder, function, pars, parNum, "call_");
        else return LLVMBuildCall(builder, function, pars, parNum, "");
    }

    @Override
    public LLVMValueRef visitUnaryOpExp(SysYParser.UnaryOpExpContext ctx){
        String op = ctx.unaryOp().getText();
        LLVMValueRef expValue = ctx.exp().accept(this);
        if (op.equals("+")){
            return expValue;
        }else if(op.equals("-")){
            LLVMBuilderRef builder = LLVMCreateBuilder();
            LLVMValueRef zero = LLVMConstInt(i32Type, 0,0);
            return LLVMBuildSub(builder, zero, expValue, "tmp_");
        }else {
            if (LLVMConstIntGetZExtValue(expValue) == 0){
                return LLVMConstInt(i32Type, 1 ,1 );
            }
            return LLVMConstInt(i32Type,0,1);
        }

    }

    @Override
    public LLVMValueRef visitExpParenthesis(SysYParser.ExpParenthesisContext ctx){
        return ctx.exp().accept(this);
    }

    @Override
    public LLVMValueRef visitPlusExp(SysYParser.PlusExpContext ctx){
        LLVMValueRef lval = ctx.exp(0).accept(this);
        LLVMValueRef rval = ctx.exp(1).accept(this);
        if (ctx.PLUS() != null){
            return LLVMBuildAdd(builder, lval, rval, "tmp_");
        }else {
            return LLVMBuildSub(builder, lval, rval, "tmp_");
        }
    }

    @Override
    public LLVMValueRef visitMulExp(SysYParser.MulExpContext ctx){
        LLVMValueRef lval = ctx.exp(0).accept(this);
        LLVMValueRef rval = ctx.exp(1).accept(this);
        if (ctx.MUL()!=null){
            return LLVMBuildMul(builder, lval, rval, "tmp_");
        }else if(ctx.DIV() != null){
            return LLVMBuildSDiv(builder, lval, rval, "tmp_");
        }else {
            return LLVMBuildSRem(builder, lval, rval, "tmp_");
        }
    }

    @Override
    public LLVMValueRef visitReturnStmt(SysYParser.ReturnStmtContext ctx){
        LLVMValueRef ret = ctx.exp() == null ? null : ctx.exp().accept(this);
        return LLVMBuildRet(builder, ret);
    }

    @Override
    public LLVMValueRef visitProgram(SysYParser.ProgramContext ctx){
        currentScope = globalScope = new GlobalScope(null);
        super.visitProgram(ctx);
        currentScope = currentScope.getEnclosingScope();
        return  null;
    }

    @Override
    public LLVMValueRef visitBlock(SysYParser.BlockContext ctx){
        LocalScope localScope = new LocalScope(currentScope);
        localScope.setName(localScope.getName() + (++localScopeNum));
        currentScope = localScope;
        super.visitBlock(ctx);
        currentScope = currentScope.getEnclosingScope();
        return null;
    }

    @Override
    public LLVMValueRef visitVarDecl(SysYParser.VarDeclContext ctx){
        for (SysYParser.VarDefContext varDefContext : ctx.varDef()){
            String name = varDefContext.IDENT().getText();
            LLVMValueRef varPointer;
            if (currentScope == globalScope) {
                varPointer = LLVMAddGlobal(module, i32Type, name);
                LLVMSetInitializer(varPointer, zero);
            } else {
                varPointer = LLVMBuildAlloca(builder, i32Type, name);
            }
            if (varDefContext.ASSIGN() != null){
                SysYParser.ExpContext expContext = varDefContext.initVal().exp();
                LLVMValueRef initVal = expContext.accept(this);
                if (currentScope == globalScope){
                    LLVMSetInitializer(varPointer, initVal);
                }else {
                    LLVMBuildStore(builder, initVal, varPointer);
                }
            }
            currentScope.define(name, varPointer,i32Type);

        }
        return null;

    }

    @Override
    public LLVMValueRef visitConstDecl(SysYParser.ConstDeclContext ctx){
        for (SysYParser.ConstDefContext constDefContext : ctx.constDef()){
            String name = constDefContext.IDENT().getText();
            LLVMValueRef varPointer;
            SysYParser.ConstExpContext constExpContext = constDefContext.constInitVal().constExp();
            LLVMValueRef initVal = constExpContext.accept(this);
            if (currentScope == globalScope) {
                varPointer = LLVMAddGlobal(module, i32Type,  name);
                LLVMSetInitializer(varPointer, zero);
                LLVMSetInitializer(varPointer, initVal);
            } else {
                varPointer = LLVMBuildAlloca(builder, i32Type, name);
                LLVMBuildStore(builder, initVal, varPointer);
            }
            currentScope.define(name, varPointer, i32Type);
        }
        return null;
    }

    @Override
    public LLVMValueRef visitLvalExp(SysYParser.LvalExpContext ctx){
        LLVMValueRef val = ctx.lVal().accept(this);
        return LLVMBuildLoad(builder, val, ctx.lVal().getText());
    }

    @Override
    public LLVMValueRef visitLVal(SysYParser.LValContext ctx){
        String name = ctx.IDENT().getText();
        return currentScope.resolve(name);
    }

    @Override
    public LLVMValueRef visitAssignStmt(SysYParser.AssignStmtContext ctx){
        LLVMValueRef varPointer = visitLVal(ctx.lVal());
        LLVMValueRef exp = ctx.exp().accept(this);
        return LLVMBuildStore(builder, exp, varPointer);
    }


    @Override
    public LLVMValueRef visitConditionStmt(SysYParser.ConditionStmtContext ctx){
        LLVMValueRef val =  ctx.cond().accept(this);
        LLVMValueRef condition = LLVMBuildICmp(builder, LLVMIntNE, zero, val, "tmp_");
        LLVMBasicBlockRef trueBl = LLVMAppendBasicBlock(function, "true");
        LLVMBasicBlockRef falseBl = LLVMAppendBasicBlock(function, "false");
        LLVMBasicBlockRef nextBl = LLVMAppendBasicBlock(function, "entry");
        LLVMBuildCondBr(builder, condition, trueBl, falseBl);
        LLVMPositionBuilderAtEnd(builder, trueBl);
        ctx.stmt(0).accept(this);
        LLVMBuildBr(builder, nextBl);
        LLVMPositionBuilderAtEnd(builder, falseBl);
        if (null != ctx.ELSE())
            ctx.stmt(1).accept(this);
        LLVMBuildBr(builder,nextBl);
        LLVMPositionBuilderAtEnd(builder, nextBl);
        return val;
    }

    @Override
    public LLVMValueRef visitLtCond(SysYParser.LtCondContext ctx){
        LLVMValueRef left = ctx.cond(0).accept(this);
        LLVMValueRef right = ctx.cond(1).accept(this);
        if (LLVMTypeOf(left).equals(i1Type)){
            left = LLVMBuildZExt(builder, left , i32Type, "tmp_");
        }
        if (LLVMTypeOf(right).equals(i1Type)){
            right = LLVMBuildZExt(builder, right, i32Type, "tmp_");
        }
        LLVMValueRef condition = null;
        if (null != ctx.LT()){
            condition = LLVMBuildICmp(builder, LLVMIntSLT, left, right, "tmp_");
        }else if(null != ctx.LE()){
            condition = LLVMBuildICmp(builder, LLVMIntSLE, left, right, "tmp_");
        } else if (null != ctx.GE()) {
            condition = LLVMBuildICmp(builder, LLVMIntSGE, left, right, "tmp_");
        } else if (null != ctx.GT()) {
            condition = LLVMBuildICmp(builder, LLVMIntSGT, left, right, "tmp_");
        }
        return LLVMBuildZExt(builder, condition, i32Type, "tmp_");
    }

    @Override
    public LLVMValueRef visitEqCond(SysYParser.EqCondContext ctx){
        LLVMValueRef left = visit(ctx.cond(0));
        LLVMValueRef right = visit(ctx.cond(1));
        if (LLVMTypeOf(left).equals(i1Type)){
            left = LLVMBuildZExt(builder, left , i32Type, "tmp_");
        }
        if (LLVMTypeOf(right).equals(i1Type)){
            right = LLVMBuildZExt(builder, right, i32Type, "tmp_");
        }
        LLVMValueRef condition = null;
        if (null != ctx.NEQ()){
            condition = LLVMBuildICmp(builder, LLVMIntNE, left,right,"tmp_");
        }else {
            condition = LLVMBuildICmp(builder, LLVMIntEQ, left, right,"tmp_");
        }
        return LLVMBuildZExt(builder, condition, i32Type, "tmp_");
    }

    @Override
    public LLVMValueRef visitAndCond(SysYParser.AndCondContext ctx) {
        LLVMValueRef left = ctx.cond(0).accept(this);
        LLVMValueRef result;
        LLVMValueRef pointer;
        LLVMBasicBlockRef next = LLVMAppendBasicBlock(function, "next");
        LLVMBasicBlockRef end = LLVMAppendBasicBlock(function, "end");
        LLVMValueRef left_i1 = LLVMBuildICmp(builder, LLVMIntNE, left, zero, "cmp_");
        pointer = LLVMBuildAlloca(builder, i1Type, "pointer");
        LLVMBuildStore(builder, left_i1, pointer);
        LLVMBuildCondBr(builder, left_i1, next, end);
        LLVMPositionBuilderAtEnd(builder, next);
        LLVMValueRef right = ctx.cond(1).accept(this);
        LLVMValueRef right_i1 = LLVMBuildICmp(builder, LLVMIntNE, right, zero, "cmp");
        result = LLVMBuildAnd(builder, left_i1, right_i1, "and_");
        LLVMBuildStore(builder, result, pointer);
        LLVMBuildBr(builder, end);
        LLVMPositionBuilderAtEnd(builder, end);
        return LLVMBuildZExt(builder, LLVMBuildLoad(builder, pointer, "load"), i32Type, "ext");
    }



    @Override
    public LLVMValueRef visitOrCond(SysYParser.OrCondContext ctx) {
        LLVMValueRef left = ctx.cond(0).accept(this);
        LLVMValueRef result;
        LLVMValueRef pointer;
        LLVMBasicBlockRef next = LLVMAppendBasicBlock(function, "next");
        LLVMBasicBlockRef end = LLVMAppendBasicBlock(function, "end");
        LLVMValueRef left_i1 = LLVMBuildICmp(builder, LLVMIntNE, left, zero, "cmp_");
        pointer = LLVMBuildAlloca(builder, i1Type, "pointer");
        LLVMBuildStore(builder, left_i1, pointer);
        LLVMBuildCondBr(builder, left_i1, end, next);
        LLVMPositionBuilderAtEnd(builder, next);
        LLVMValueRef right = ctx.cond(1).accept(this);
        LLVMValueRef right_i1 = LLVMBuildICmp(builder, LLVMIntNE, right, zero, "cmp");
        result = LLVMBuildOr(builder, left_i1, right_i1, "and_");
        LLVMBuildStore(builder, result, pointer);
        LLVMBuildBr(builder, end);
        LLVMPositionBuilderAtEnd(builder, end);
        return LLVMBuildZExt(builder, LLVMBuildLoad(builder, pointer, "load"), i32Type, "ext");
    }

    @Override
    public LLVMValueRef visitExpCond(SysYParser.ExpCondContext ctx){
        return ctx.exp().accept(this);
    }

    @Override
    public LLVMValueRef visitWhileStmt(SysYParser.WhileStmtContext ctx){
        LLVMBasicBlockRef cond = LLVMAppendBasicBlock(function, "tmp_");
        LLVMBasicBlockRef body = LLVMAppendBasicBlock(function, "tmp_");
        LLVMBasicBlockRef next = LLVMAppendBasicBlock(function, "tmp_");
        LLVMBuildBr(builder, cond);

        LLVMPositionBuilderAtEnd(builder, cond);
        LLVMValueRef val  = ctx.cond().accept(this);
        LLVMValueRef condition = LLVMBuildICmp(builder, LLVMIntNE, zero, val, "tmp_");
        LLVMBuildCondBr(builder, condition, body, next);

        LLVMPositionBuilderAtEnd(builder, body);
        whileCond.push(cond);
        whileNext.push(next);
        ctx.stmt().accept(this);

        LLVMBuildBr(builder, cond);
        whileCond.pop();
        whileNext.pop();
        LLVMBuildBr(builder, next);

        LLVMPositionBuilderAtEnd(builder, next);

        return null;
    }

    @Override
    public LLVMValueRef visitBreakStmt(SysYParser.BreakStmtContext ctx){
        return LLVMBuildBr(builder, whileNext.peek());
    }

    @Override
    public LLVMValueRef visitContinueStmt(SysYParser.ContinueStmtContext ctx){

        return LLVMBuildBr(builder,whileCond.peek());
    }



}