import org.antlr.v4.runtime.tree.TerminalNode;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;

import java.util.List;
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

    public Stack<LLVMBasicBlockRef> whileCond = new Stack<>();
    public Stack<LLVMBasicBlockRef> whileNext = new Stack<>();

    public boolean isAddr = false;


    public LLVMVisitor(){
        //初始化LLVM
        LLVMInitializeCore(LLVMGetGlobalPassRegistry());
        LLVMLinkInMCJIT();
        LLVMInitializeNativeAsmPrinter();
        LLVMInitializeNativeAsmParser();
        LLVMInitializeNativeTarget();
    }


    @Override
    public LLVMValueRef visitFuncDef(SysYParser.FuncDefContext ctx){
        String name = ctx.IDENT().getText();
        LLVMTypeRef ft1 = ctx.funcType().getText().equals("int") ? i32Type : voidType;

        int parNum = null != ctx.funcFParams()  ? ctx.funcFParams().funcFParam().size() : 0;
        PointerPointer<Pointer> parTys = new PointerPointer<>(parNum);
        for(int i = 0;i<parNum;i++) {
            boolean flag = ctx.funcFParams().funcFParam(i).getText().contains("[");
            parTys.put(i, (flag ? LLVMPointerType(i32Type, 0) : i32Type));
        }

        function = LLVMAddFunction(module, name, LLVMFunctionType(ft1, parTys,parNum,0));
        LLVMPositionBuilderAtEnd(builder, LLVMAppendBasicBlock(function, name + "Entry"));

        currentScope.define(name, function,ft1);
        currentScope = new LocalScope(currentScope);

        for (int i = 0;i < parNum;i++){
            LLVMValueRef parPointer = null;
            if (!ctx.funcFParams().funcFParam(i).getText().contains("[")){
                parPointer = LLVMBuildAlloca(builder, i32Type,ctx.funcFParams().funcFParam(i).IDENT().getText());
                currentScope.define(ctx.funcFParams().funcFParam(i).IDENT().getText(), parPointer,i32Type);
                LLVMBuildStore(builder, LLVMGetParam(function, i), parPointer);
        }else {
                parPointer = LLVMBuildAlloca(builder, LLVMPointerType(i32Type, 0),ctx.funcFParams().funcFParam(i).IDENT().getText());
                currentScope.define(ctx.funcFParams().funcFParam(i).IDENT().getText(), parPointer,LLVMPointerType(i32Type, 0));
                LLVMBuildStore(builder, LLVMGetParam(function, i), parPointer);
            }
        }

        super.visitFuncDef(ctx);
        currentScope = currentScope.getEnclosingScope();
        if (ctx.funcType().getText().equals("void")) LLVMBuildRet(builder, null);
        else LLVMBuildRet(builder, zero);
        return function;
    }

    @Override
    public LLVMValueRef visitCallFuncExp(SysYParser.CallFuncExpContext ctx){
        LLVMValueRef function = currentScope.resolve(ctx.IDENT().getText());
        int parNum = (null != ctx.funcRParams() ) ? ctx.funcRParams().param().size() : 0;
        PointerPointer<Pointer> pars = new PointerPointer<>(parNum);
        for (int i = 0;i<parNum;i++){
            pars.put(i, ctx.funcRParams().param(i).exp().accept(this));
        }
        if (currentScope.reolveType(ctx.IDENT().getText()).equals(i32Type))
        return LLVMBuildCall(builder, function, pars, parNum, "call_");
        else return LLVMBuildCall(builder, function, pars, parNum, "");
    }

    @Override
    public LLVMValueRef visitPlusExp(SysYParser.PlusExpContext ctx){
        LLVMValueRef lval = ctx.exp(0).accept(this);
        LLVMValueRef rval = ctx.exp(1).accept(this);
        return ctx.PLUS()==null ?  LLVMBuildSub(builder, lval, rval, "tmp_") : LLVMBuildAdd(builder, lval, rval, "tmp_");
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
        }else { // 取非
            int tmp = LLVMConstIntGetZExtValue(expValue) == 0 ? 1 : 0;
            return LLVMConstInt(i32Type,tmp,1);
        }
    }

    @Override
    public LLVMValueRef visitExpParenthesis(SysYParser.ExpParenthesisContext ctx){
        return ctx.exp().accept(this);
    }


    @Override
    public LLVMValueRef visitMulExp(SysYParser.MulExpContext ctx){
        LLVMValueRef lval = ctx.exp(0).accept(this);
        LLVMValueRef rval = ctx.exp(1).accept(this);
        if (ctx.DIV()!=null){
            return LLVMBuildSDiv(builder, lval, rval, "tmp_");
        }else if(ctx.MUL() != null){
            return LLVMBuildMul(builder, lval, rval, "tmp_");
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
    public LLVMValueRef visitOrCond(SysYParser.OrCondContext ctx) {
        LLVMValueRef left = ctx.cond(0).accept(this);
        LLVMValueRef result;
        LLVMValueRef pointer;
        LLVMBasicBlockRef next = LLVMAppendBasicBlock(function, "next_");
        LLVMBasicBlockRef end = LLVMAppendBasicBlock(function, "end_");
        LLVMValueRef left_i1 = LLVMBuildICmp(builder, LLVMIntNE, left, zero, "cmp_");
        pointer = LLVMBuildAlloca(builder, i1Type, "pointer_");
        LLVMBuildStore(builder, left_i1, pointer);
        LLVMBuildCondBr(builder, left_i1, end, next);
        LLVMPositionBuilderAtEnd(builder, next);
        LLVMValueRef right = ctx.cond(1).accept(this);
        LLVMValueRef right_i1 = LLVMBuildICmp(builder, LLVMIntNE, right, zero, "cmp_");
        result = LLVMBuildOr(builder, left_i1, right_i1, "and_");
        LLVMBuildStore(builder, result, pointer);
        LLVMBuildBr(builder, end);
        LLVMPositionBuilderAtEnd(builder, end);
        return LLVMBuildZExt(builder, LLVMBuildLoad(builder, pointer, "load_"), i32Type, "ext_");
    }

    @Override
    public LLVMValueRef visitBlock(SysYParser.BlockContext ctx){
        LocalScope localScope = new LocalScope(currentScope);
        localScope.setName(localScope.getName());
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
            int num_in_array = 0;
            LLVMTypeRef varT = i32Type;

            for (SysYParser.ConstExpContext constExpContext : varDefContext.constExp()){
                num_in_array = Integer.parseInt(Helper.toDec(constExpContext.getText()));
                varT = LLVMArrayType(i32Type, num_in_array);
            }

            if (currentScope != globalScope) {
                varPointer = LLVMBuildAlloca(builder, varT, name);
            } else {
                varPointer = LLVMAddGlobal(module, varT, name);
                if (num_in_array != 0){
                    PointerPointer<Pointer> pp = new PointerPointer<>(num_in_array);
                    for (int i = 1; i<= num_in_array;i++){
                        pp.put(i-1, zero);
                    }
                    LLVMSetInitializer(varPointer, LLVMConstArray(varT,pp,num_in_array));
                }else {
                    LLVMSetInitializer(varPointer, zero);
                }
            }


            if (varDefContext.ASSIGN() != null){
                SysYParser.ExpContext expContext = varDefContext.initVal().exp();
                if (null != expContext ) {
                    if (currentScope == globalScope) {
                        LLVMSetInitializer(varPointer, expContext.accept(this));
                    } else {
                        LLVMBuildStore(builder, expContext.accept(this), varPointer);
                    }
                }else {
                    int tmp = varDefContext.initVal().initVal().size();
                    if (globalScope == currentScope){
                        PointerPointer<Pointer> pp = new PointerPointer<>(num_in_array);
                        for (int i = 0;i<tmp;i++){
                            pp.put(i, varDefContext.initVal().initVal(i).exp().accept(this));
                        }
                        if (tmp < num_in_array){
                            for (int i = tmp ;i < num_in_array; i++){
                                pp.put(i, zero);
                            }
                        }
                        LLVMValueRef initAr = LLVMConstArray(varT, pp, num_in_array);
                        LLVMSetInitializer(varPointer, initAr);
                    }else {
                        LLVMValueRef initAr[] = new LLVMValueRef[num_in_array];
                        for (int i = 0;i<tmp;i++){
                                initAr[i] = varDefContext.initVal().initVal(i).exp().accept(this);
                        }
                        if (tmp < num_in_array){
                            for (int i = tmp; i< num_in_array; i++){
                                initAr[i] = LLVMConstInt(i32Type, 0, 0);
                            }
                        }
                        Helper.helperArray(builder, num_in_array, varPointer, initAr);
                    }
                }
            }
            currentScope.define(name, varPointer,varT);
        }
        return null;

    }

    @Override
    public LLVMValueRef visitConstDecl(SysYParser.ConstDeclContext ctx){
        for (SysYParser.ConstDefContext constDefContext : ctx.constDef()){
            String name = constDefContext.IDENT().getText();
            LLVMValueRef varPointer;
            int num_in_array = 0;
            LLVMTypeRef varT = i32Type;

            for (SysYParser.ConstExpContext constExpContext : constDefContext.constExp()){
                num_in_array = Integer.parseInt(Helper.toDec(constExpContext.getText()));
                varT = LLVMArrayType(varT, num_in_array);
            }
            
            if (globalScope == currentScope) {
                varPointer = LLVMAddGlobal(module, varT,  name);
                LLVMSetInitializer(varPointer, zero);
            } else {
                varPointer = LLVMBuildAlloca(builder, varT, name);
            }

            SysYParser.ConstExpContext constExpContext = constDefContext.constInitVal().constExp();
            if (constExpContext != null) {
                LLVMValueRef initVal = visit(constExpContext);
                if (globalScope == currentScope) {
                    LLVMSetInitializer(varPointer, initVal);
                } else {
                    LLVMBuildStore(builder, initVal, varPointer);
                }
            }else {
                int tmp = constDefContext.constInitVal().constInitVal().size();
                if (currentScope == globalScope){
                    PointerPointer<LLVMValueRef> pp = new PointerPointer<>(num_in_array);
                    for (int i = 0;i<tmp;i++){
                        pp.put(i, constDefContext.constInitVal().constInitVal(i).constExp().accept(this));
                    }
                    if (tmp < num_in_array){
                        for (int i = tmp; i<num_in_array;i++){
                            pp.put(i, zero);
                        }
                    }
                    LLVMValueRef initArray = LLVMConstArray(varT, pp, num_in_array);
                    LLVMSetInitializer(varPointer, initArray);
                }else {
                    LLVMValueRef initAr[] = new  LLVMValueRef[num_in_array];
                    for (int i = 0;i<tmp;i++){
                        initAr[i] = constDefContext.constInitVal().constInitVal(i).constExp().accept(this);
                    }
                    if (tmp < num_in_array){
                        for (int i = tmp; i<num_in_array; i++){
                            initAr[i] = LLVMConstInt(i32Type, 0, 0);
                        }
                    }
                    Helper.helperArray(builder, num_in_array, varPointer, initAr);
                }
            }
            currentScope.define(name, varPointer, varT);
        }
        return null;
    }


    @Override
    public LLVMValueRef visitLvalExp(SysYParser.LvalExpContext ctx){
        LLVMValueRef val = ctx.lVal().accept(this);
        if (isAddr){
            isAddr = false;
            return val;
        }
        return LLVMBuildLoad(builder, val, ctx.lVal().getText());
    }

    @Override
    public LLVMValueRef visitLVal(SysYParser.LValContext ctx){
        String name = ctx.IDENT().getText();
        LLVMValueRef varPointer = currentScope.resolve(name);
        LLVMTypeRef varT = currentScope.reolveType(name);
        LLVMTypeRef intPointerType = LLVMPointerType(i32Type, 0);
        List<SysYParser.ExpContext> exp = ctx.exp();
        LLVMValueRef[] ap = null;
        PointerPointer<LLVMValueRef> index =  null;
        if (varT.equals(i32Type)){
            return varPointer;
        }else if(varT.equals(intPointerType)){
            if (exp.size() > 0){
                ap = new LLVMValueRef[1];
                ap[0] = ctx.exp(0).accept(this);
                index = new PointerPointer<>(ap);
                LLVMValueRef pointer = LLVMBuildLoad(builder, varPointer, name);
                return LLVMBuildGEP(builder, pointer, index, 1, name);
            }else {
                return varPointer;
            }
        }else {
            ap = new LLVMValueRef[2];
            ap[0] = zero;
            ap[1] = (exp.size()>0) ? exp.get(0).accept(this) : zero;
            if (exp.size() <= 0) isAddr = true;
            index = new PointerPointer<>(ap);
            return LLVMBuildGEP(builder, varPointer, index, 2, name);
        }
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

        LLVMBasicBlockRef trueBl = LLVMAppendBasicBlock(function, "true_");
        LLVMBasicBlockRef falseBl = LLVMAppendBasicBlock(function, "false_");
        LLVMBasicBlockRef nextBl = LLVMAppendBasicBlock(function, "entry_");

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
        if (LLVMTypeOf(left).equals(i1Type)){
            left = LLVMBuildZExt(builder, left , i32Type, "tmp_");
        }

        LLVMValueRef right = ctx.cond(1).accept(this);
        if (LLVMTypeOf(right).equals(i1Type)){
            right = LLVMBuildZExt(builder, right, i32Type, "tmp_");
        }

        LLVMValueRef condition = null;
        if (ctx.LT() != null)
                condition = LLVMBuildICmp(builder, LLVMIntSLT, left, right, "tmp_");
        else if(ctx.LE() != null)
            condition = LLVMBuildICmp(builder, LLVMIntSLE, left, right, "tmp_");
        else if(ctx.GT() != null)
                condition = LLVMBuildICmp(builder, LLVMIntSGT, left, right, "tmp_");
        else
                condition = LLVMBuildICmp(builder, LLVMIntSGE, left, right, "tmp_");
        return LLVMBuildZExt(builder, condition, i32Type, "tmp_");
    }

    @Override
    public LLVMValueRef visitTerminal(TerminalNode terminalNode){
        if (terminalNode.getSymbol().getType() == SysYParser.INTEGER_CONST){
            return LLVMConstInt(i32Type, (int) Integer.parseInt(Helper.toDec(terminalNode.getText())), 1);
        }
        return super.visitTerminal(terminalNode);
    }

    @Override
    public LLVMValueRef visitEqCond(SysYParser.EqCondContext ctx){
        LLVMValueRef left = visit(ctx.cond(0));
        if (LLVMTypeOf(left).equals(i1Type)){
            left = LLVMBuildZExt(builder, left , i32Type, "tmp_");
        }

        LLVMValueRef right = visit(ctx.cond(1));
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
        return zero;
    }

    @Override
    public LLVMValueRef visitAndCond(SysYParser.AndCondContext ctx) {
        LLVMValueRef left = ctx.cond(0).accept(this);

        LLVMValueRef result;
        LLVMValueRef pointer;

        LLVMBasicBlockRef next = LLVMAppendBasicBlock(function, "next_");
        LLVMBasicBlockRef end = LLVMAppendBasicBlock(function, "end_");

        LLVMValueRef left_i1 = LLVMBuildICmp(builder, LLVMIntNE, left, zero, "cmp_");
        pointer = LLVMBuildAlloca(builder, i1Type, "pointer_");
        LLVMBuildStore(builder, left_i1, pointer);
        LLVMBuildCondBr(builder, left_i1, next, end);

        LLVMPositionBuilderAtEnd(builder, next);
        LLVMValueRef right = ctx.cond(1).accept(this);
        LLVMValueRef right_i1 = LLVMBuildICmp(builder, LLVMIntNE, right, zero, "cmp_");

        result = LLVMBuildAnd(builder, left_i1, right_i1, "and_");
        LLVMBuildStore(builder, result, pointer);
        LLVMBuildBr(builder, end);

        LLVMPositionBuilderAtEnd(builder, end);
        return LLVMBuildZExt(builder, LLVMBuildLoad(builder, pointer, "load_"), i32Type, "ext_");
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