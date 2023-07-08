import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.bytedeco.llvm.LLVM.*;
import org.bytedeco.llvm.global.LLVM;

import static org.bytedeco.llvm.global.LLVM.*;


public class LlvmVisitor extends SysYParserBaseVisitor<LLVMValueRef> {

    //创建module
    public LLVMModuleRef module = LLVMModuleCreateWithName("module");

    //初始化IRBuilder，后续将使用这个builder去生成LLVM IR
    public LLVMBuilderRef builder = LLVMCreateBuilder();

    //考虑到我们的语言中仅存在int一个基本类型，可以通过下面的语句为LLVM的int型重命名方便以后使用
    public LLVMTypeRef i32Type = LLVMInt32Type();

    public LlvmVisitor(){
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
        return null;
    }

    @Override
    public LLVMValueRef visitFuncDef(SysYParser.FuncDefContext ctx){
        LLVMTypeRef ft = LLVMFunctionType(i32Type, LLVMVoidType(),0,0);
        String name = ctx.IDENT().getText();
        LLVMValueRef function = LLVMAddFunction(module, name, ft);
        LLVMBasicBlockRef mainEntry = LLVMAppendBasicBlock(function, "mainENtry");
        LLVMPositionBuilderAtEnd(builder, mainEntry);
        super.visitFuncDef(ctx);
        return function;
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
        LLVMBuilderRef builder = LLVMCreateBuilder();
        LLVMDisposeBuilder(builder);
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
        LLVMBuilderRef builder = LLVMCreateBuilder();
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
        LLVMValueRef ret = ctx.exp().accept(this);
        return LLVMBuildRet(builder, ret);
    }

}