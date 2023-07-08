import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.*;

public class TypeCheckListener extends SysYParserBaseListener{
    private GlobalScope globalScope = null;
    private Scope currentScope = null;
    private int localScopeCounter = 0;
    public boolean hasError = false;
    public boolean skipFuncScope = false;
    private final List<Object> msgToPrint = new ArrayList<>();
    public List<Object> getMsgToPrint() {return msgToPrint;}
    @Override
    public void enterProgram(SysYParser.ProgramContext ctx) {
        globalScope = new GlobalScope(null);
        currentScope = globalScope;
    }
    @Override
    public void enterFuncDef(SysYParser.FuncDefContext ctx) {
        if (!skipFuncScope) {
            String returnTypeName = ctx.funcType().getText();
            Symbol returnSymbol = globalScope.resolve(returnTypeName);
            String funcName =ctx.IDENT().getText();
            Symbol resolveSymbol = globalScope.resolve(funcName);
            FunctionScope functionScope = new FunctionScope(funcName, currentScope);
            FunctionType functionType = new FunctionType(functionScope, returnSymbol.getType());
            FunctionSymbol functionSymbol = new FunctionSymbol(functionType);
            if (resolveSymbol == null) {
                currentScope.define(functionSymbol);
                currentScope = functionScope;
                List<SysYParser.FuncFParamContext> funcFParamContexts = new LinkedList<>();
                if (hasParams(ctx)) funcFParamContexts.addAll(ctx.funcFParams().funcFParam());
                for (SysYParser.FuncFParamContext funcFParamContext : funcFParamContexts) {
                    String paramName = funcFParamContext.IDENT().getText();
                    Symbol paramSymbol = currentScope.resolveInConflictScope(paramName);
                    if (paramSymbol == null) {
                        defineParam(funcFParamContext, functionType);
                    } else {
                        hasError = Helper.printError(3, getLine(funcFParamContext));
                    }
                }
            } else {
                skipFuncScope = true;
                hasError = Helper.printError(4, getLine(ctx));
            }
        }
    }
    @Override
    public void enterBlock(SysYParser.BlockContext ctx) {
        if (!skipFuncScope) {
            LocalScope localScope = new LocalScope(currentScope);
            String localScopeName = localScope.getName() + localScopeCounter;
            localScope.setName(localScopeName);
            localScopeCounter++;
            currentScope = localScope;
        }
    }
    @Override
    public void exitProgram(SysYParser.ProgramContext ctx) {
        currentScope = currentScope.getEnclosingScope();
    }
    @Override
    public void exitFuncDef(SysYParser.FuncDefContext ctx) {
        currentScope = skipFuncScope ? currentScope :currentScope.getEnclosingScope();
        skipFuncScope = false;
    }
    @Override
    public void exitBlock(SysYParser.BlockContext ctx) {
        currentScope = skipFuncScope ? currentScope : currentScope.getEnclosingScope();
    }
    @Override
    public void enterConstDecl(SysYParser.ConstDeclContext ctx) {
        if (!skipFuncScope) {
            String typeName = ctx.bType().getText();
            Symbol typeSymbol = globalScope.resolve(typeName);
            for (SysYParser.ConstDefContext constDef : ctx.constDef()) {
                String constName = constDef.IDENT().getText();
                Symbol labelSymbol = currentScope.resolveInConflictScope(constName);
                List<Object> constExps = new LinkedList<>();
                if (isArray(constDef)) constExps.addAll(constDef.constExp());
                VariableSymbol constSymbol = new VariableSymbol(constName, generateArray(constExps, (BaseType) typeSymbol.getType()));
                Type constInitValType = null;
                if (hasConstInitVal(constDef)) {
                    constInitValType = resolveConstInitValType(constDef.constInitVal(), (BaseType) typeSymbol.getType());
                }
                if (labelSymbol == null) {
                    if (constInitValType != null && !constInitValType.equals(constSymbol.getType())) {
                        hasError = Helper.printError(5, getLine(constDef));
                    }
                    currentScope.define(constSymbol);
                } else {
                    hasError = Helper.printError(3, getLine(ctx));
                }
            }
        }
    }

    @Override
    public void enterVarDecl(SysYParser.VarDeclContext ctx) {
        if (!skipFuncScope) {
            String typeName = ctx.bType().getText();
            Symbol typeSymbol = globalScope.resolve(typeName);
            for (SysYParser.VarDefContext varDef : ctx.varDef()) {
                String varName = varDef.IDENT().getText();
                Symbol labelSymbol = currentScope.resolveInConflictScope(varName);
                List<Object> constExps = new LinkedList<>();
                if (isArray(varDef)) constExps.addAll(varDef.constExp());
                VariableSymbol variableSymbol = new VariableSymbol(varName, generateArray(constExps, (BaseType) typeSymbol.getType()));
                Type initValType = null;
                if (hasInitVal(varDef)) {
                    initValType = resolveInitVal(varDef.initVal(), (BaseType) typeSymbol.getType());
                }
                if (labelSymbol == null) {
                    if (initValType != null && !initValType.equals(variableSymbol.getType())) {
                        hasError = Helper.printError(5, getLine(varDef));
                    }
                    currentScope.define(variableSymbol);
                } else {
                    hasError = Helper.printError(3, getLine(ctx));
                }
            }
        }
    }
    @Override
    public void enterAssignStmt(SysYParser.AssignStmtContext ctx) {
        if (!skipFuncScope) {
            Type lValType = resolveLValType(ctx.lVal());
            Type rValType = resolveExpType(ctx.exp());
            if (lValType != null) {
                if (lValType instanceof FunctionType) {
                    hasError = Helper.printError(11, getLine(ctx));
                } else {
                    if (rValType != null && !lValType.equals(rValType)) {
                        hasError = Helper.printError(5, getLine(ctx));
                    }
                }
            }
        }
    }
    @Override
    public void enterReturnStmt(SysYParser.ReturnStmtContext ctx) {
        if (!skipFuncScope) {
            Type expReturnType = resolveExpType(ctx.exp());
            FunctionType functionType = getNearestFunctionType();
            Type funcReturnType = functionType.getReturnType();
            if (funcReturnType.equals(BaseType.getTypeInt())) {
                funcReturnType = new ArrayType(0, funcReturnType);
            }
            if (expReturnType != null && !(expReturnType.equals(funcReturnType))) {
                hasError = Helper.printError(7, getLine(ctx));
            }
        }
    }
    @Override
    public void enterExpStmt(SysYParser.ExpStmtContext ctx) {
        if (!skipFuncScope) resolveExpType(ctx.exp());
    }
    @Override
    public void enterIfStmt(SysYParser.IfStmtContext ctx) {
        if (!skipFuncScope) resolveCondType(ctx.cond());
    }

    @Override
    public void enterWhileStmt(SysYParser.WhileStmtContext ctx) {
        if (!skipFuncScope) resolveCondType(ctx.cond());
    }
    private boolean hasParams(SysYParser.FuncDefContext ctx) {
        return ctx.funcFParams() != null;
    }
    private boolean isArray(SysYParser.ConstDefContext ctx) {
        return !ctx.constExp().isEmpty();
    }
    private boolean isArray(SysYParser.VarDefContext ctx) {
        return !ctx.constExp().isEmpty();
    }
    private void defineParam(SysYParser.FuncFParamContext ctx, FunctionType functionType) {
        String typeName = ctx.bType().getText();
        Symbol typeSymbol = globalScope.resolve(typeName);
        String paraName = ctx.IDENT().getText();
        VariableSymbol variableSymbol = new VariableSymbol(paraName, getParaType(ctx, (BaseType) typeSymbol.getType()));
        currentScope.define(variableSymbol);
        functionType.addParamType(variableSymbol.getType());
    }

    private ArrayType generateArray(List<Object> indexList, BaseType baseType) {
        if (indexList.isEmpty()) return new ArrayType(0, baseType);
        else {
            Object index = indexList.get(0);
            indexList.remove(0);
            return new ArrayType(index, generateArray(indexList, baseType));
        }
    }

    private Type resolveConstInitValType(SysYParser.ConstInitValContext ctx, BaseType baseType) {
        if (ctx instanceof SysYParser.ConstExpConstInitValContext) {
            return resolveExpType(((SysYParser.ConstExpConstInitValContext) ctx).constExp().exp());
        } else {
            SysYParser.ArrayConstInitValContext arrayConstInitValContext = (SysYParser.ArrayConstInitValContext) ctx;
            if (!(arrayConstInitValContext.constInitVal().isEmpty())) {
                int count = arrayConstInitValContext.constInitVal().size();
                Type subType = resolveConstInitValType(arrayConstInitValContext.constInitVal(0), baseType);
                if (subType != null) return new ArrayType(count, subType);
            } else {
                return new ArrayType(-1, new ArrayType(0, baseType));
            }
        }
        return null;
    }

    private Type resolveInitVal(SysYParser.InitValContext ctx, BaseType baseType) {
        if (ctx instanceof SysYParser.ExpInitValContext) {
            return resolveExpType(((SysYParser.ExpInitValContext) ctx).exp());
        } else {
            SysYParser.ArrayInitValContext arrayInitValContext = (SysYParser.ArrayInitValContext) ctx;
            if (!arrayInitValContext.initVal().isEmpty()) {
                int count = arrayInitValContext.initVal().size();
                Type subType = resolveInitVal(arrayInitValContext.initVal(0), baseType);
                if (subType != null) {
                    return new ArrayType(count, subType);
                }
            } else {
                return new ArrayType(-1, new ArrayType(0, baseType));
            }
        }
        return null;
    }

    private ArrayType getParaType(SysYParser.FuncFParamContext ctx, BaseType type) {
        List<Object> indexList = new LinkedList<>();
        if (!ctx.L_BRACKT().isEmpty()) indexList.add(-1);
        indexList.addAll(ctx.exp());
        return generateArray(indexList, type);
    }

    private Type resolveExpType(SysYParser.ExpContext expContext) {
        if (expContext instanceof SysYParser.LValExpContext) {
            return resolveLValType(((SysYParser.LValExpContext) expContext).lVal());
        } else if (expContext instanceof SysYParser.ParenExpContext) {
            SysYParser.ParenExpContext parenExpContext = (SysYParser.ParenExpContext) expContext;
            return resolveExpType(parenExpContext.exp());
        } else if (expContext instanceof SysYParser.NumberExpContext) {
            return new ArrayType(0, BaseType.getTypeInt());
        } else if (expContext instanceof SysYParser.CallExpContext) {
            SysYParser.CallExpContext callExpContext = (SysYParser.CallExpContext) expContext;
            return resolveCallExp(callExpContext);
        } else if (expContext instanceof SysYParser.UnaryExpContext) {
            SysYParser.UnaryExpContext unaryExpContext = (SysYParser.UnaryExpContext) expContext;
            Type unaryExpType = resolveExpType(unaryExpContext.exp());
            return resolveOneIntOPType(unaryExpType, unaryExpContext);
        } else if (expContext instanceof SysYParser.MulDivModExpContext) {
            SysYParser.MulDivModExpContext mulDivModExpContext = (SysYParser.MulDivModExpContext) expContext;
            Type lhsType = resolveExpType(mulDivModExpContext.lhs);
            Type rhsType = resolveExpType(mulDivModExpContext.rhs);
            return resolveTwoIntOPType(lhsType, rhsType, mulDivModExpContext);
        } else if (expContext instanceof SysYParser.PlusMinusExpContext) {
            SysYParser.PlusMinusExpContext plusMinusExpContext = (SysYParser.PlusMinusExpContext) expContext;
            Type lhsType = resolveExpType(plusMinusExpContext.lhs);
            Type rhsType = resolveExpType(plusMinusExpContext.rhs);
            return resolveTwoIntOPType(lhsType, rhsType, plusMinusExpContext);
        } else {
            return BaseType.getTypeVoid();
        }
    }

    private Type resolveLValType(SysYParser.LValContext lValContext) {
        String lValName = lValContext.IDENT().getText();
        Symbol lValSymbol = currentScope.resolve(lValName);
        if (lValSymbol == null) {
            hasError = Helper.printError(1, getLine(lValContext));
        } else {
            Type labelType = lValSymbol.getType();
            if (lValSymbol instanceof VariableSymbol && lValContext.getChildCount() > 1) {
                int indexSize = lValContext.L_BRACKT().size();
                for (int i = 0; i < indexSize; i++) {
                    if (labelType instanceof ArrayType && ((ArrayType) labelType).getType() instanceof ArrayType) {
                        labelType = ((ArrayType) labelType).getType();
                    } else {
                        hasError = Helper.printError(9, getLine(lValContext));
                        return null;
                    }
                }
            }
            return labelType;
        }
        return null;
    }

    private Type resolveCallExp(SysYParser.CallExpContext callExpContext) {
        String funcName = callExpContext.IDENT().getText();
        Symbol funcSymbol = currentScope.resolve(funcName);
        if (funcSymbol == null) {
            hasError = Helper.printError(2, getLine(callExpContext));
        } else if (!(funcSymbol instanceof FunctionSymbol)) {
           hasError =  Helper.printError(10, getLine(callExpContext));
        } else {
            FunctionType functionType = (FunctionType) funcSymbol.getType();
            if (checkFuncRParams(callExpContext, functionType)) {
                return resolveReturnType(functionType);
            } else {
                hasError = Helper.printError(8, getLine(callExpContext));
            }
        }
        return null;
    }

    private boolean checkFuncRParams(SysYParser.CallExpContext callExpContext, FunctionType functionType) {
        int rParamSize = 0;
        int fParamSize = functionType.getParamSize();
        List<SysYParser.ParamContext> paramContexts = new LinkedList<>();

        if (callExpContext.funcRParams() != null) {
            paramContexts.addAll(callExpContext.funcRParams().param());// NullPointerException, 之前没有看到funcRParams之后有个问号
            rParamSize = paramContexts.size();
        }

        int i = 0;
        int j = 0;
        for (; i < rParamSize && j < fParamSize; j++) {
            SysYParser.ParamContext paramContext = paramContexts.get(i);
            Type rParamType = resolveExpType(paramContext.exp());
            Type fParamType = functionType.getParamTypes(j);
            if (rParamType != null) {
                if (!fParamType.equals(rParamType)) {
                    return false;
                }
                i++;
            }
        }
        return i == rParamSize && j == fParamSize;
    }

    private Type resolveReturnType(FunctionType functionType) {
        Type returnType = functionType.getReturnType();
        if (returnType.equals(BaseType.getTypeInt())) {
            return new ArrayType(0, returnType);
        } else {
            return returnType;
        }
    }


    private Type resolveCondType(SysYParser.CondContext ctx) {
        if (ctx instanceof SysYParser.ExpCondContext) {
            return resolveExpType(((SysYParser.ExpCondContext) ctx).exp());
        } else if (ctx instanceof SysYParser.GLCondContext) {
            Type lCondType = resolveCondType(((SysYParser.GLCondContext) ctx).cond(0));
            Type rCondType = resolveCondType(((SysYParser.GLCondContext) ctx).cond(1));
            return resolveTwoIntOPType(lCondType, rCondType, ctx);
        } else if (ctx instanceof SysYParser.EQCondContext){
            Type lCondType = resolveCondType(((SysYParser.EQCondContext) ctx).cond(0));
            Type rCondType = resolveCondType(((SysYParser.EQCondContext) ctx).cond(1));
            return resolveTwoIntOPType(lCondType, rCondType, ctx);
        } else if (ctx instanceof SysYParser.AndCondContext) {
            Type lCondType = resolveCondType(((SysYParser.AndCondContext) ctx).cond(0));
            Type rCondType = resolveCondType(((SysYParser.AndCondContext) ctx).cond(1));
            return resolveTwoIntOPType(lCondType, rCondType, ctx);
        } else if (ctx instanceof SysYParser.OrCondContext) {
            Type lCondType = resolveCondType(((SysYParser.OrCondContext) ctx).cond(0));
            Type rCondType = resolveCondType(((SysYParser.OrCondContext) ctx).cond(1));
            return resolveTwoIntOPType(lCondType, rCondType, ctx);
        }
        return null;
    }

    private Type resolveOneIntOPType(Type operandType, ParserRuleContext ctx) {
        if (operandType != null && !isIntType(operandType)) {
            hasError = Helper.printError(6, getLine(ctx));
        }
        return operandType;
    }

    private Type resolveTwoIntOPType(Type leftOperandType, Type rightOperandType, ParserRuleContext ctx) {
        if (leftOperandType != null && rightOperandType != null) {
            if (leftOperandType.equals(rightOperandType) && isIntType(leftOperandType)) {
                return leftOperandType;
            } else {
                hasError = Helper.printError(6, getLine(ctx));
            }
        }
        return null;
    }

    private boolean hasConstInitVal(SysYParser.ConstDefContext ctx) {
        return ctx.constInitVal() != null;
    }

    private boolean hasInitVal(SysYParser.VarDefContext ctx) {
        return ctx.initVal() != null;
    }

    private boolean isIntType(Type t) {
        return t instanceof ArrayType && ((ArrayType) t).getType().equals(BaseType.getTypeInt());
    }

    private FunctionType getNearestFunctionType() {
        Scope scopePointer = currentScope;
        while (!(scopePointer instanceof FunctionScope)) {
            scopePointer = scopePointer.getEnclosingScope();
        }
        String funcName = scopePointer.getName();
        return (FunctionType) scopePointer.getEnclosingScope().resolve(funcName).getType();
    }

    private int getLine(ParserRuleContext ctx) {
        return ctx.getStart().getLine();
    }


    public void enterEveryRule(ParserRuleContext ctx) {
        String ruleName = SysYParser.ruleNames[ctx.getRuleIndex()];
        msgToPrint.add(Helper.getSpace(ctx.depth()-1));
        msgToPrint.add(ruleName.substring(0, 1).toUpperCase() + ruleName.substring(1) + "\n");
    }

    @Override
    public void visitTerminal(TerminalNode node) {
        RuleNode parent = (RuleNode) node.getParent();
        int depth = parent.getRuleContext().depth() + 1;
        String text = node.getSymbol().getText();
        int typeIndex = node.getSymbol().getType();
        if (typeIndex > 0) {
            String type = SysYLexer.ruleNames[typeIndex - 1];
            if (!Helper.getColor(type).equals("error")) {
                if (type.equals("INTEGER_CONST"))    text = Helper.toDec(text);
                msgToPrint.add("  ".repeat(Math.max(0, depth-1)));
                msgToPrint.add(text + " " + type + "[" + Helper.getColor(type) + "]" + "\n");
            }
        }
    }
}