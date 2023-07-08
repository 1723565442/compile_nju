import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;
import static org.bytedeco.llvm.global.LLVM.*;
import static org.bytedeco.llvm.global.LLVM.LLVMInt32Type;

public class Helper {


    public static String[] errors = {
            "Undefined variable",
            "Undefined function",
            "Redefined variable", //变量重定义
            "Redefined function", //
            "Type mismatched for assignment", //等号两边的类型不同
            "Type mismatched for operands",  //操作符两边的类型不同
            "Type mismatched for return",  //返回值与函数声明的类型不同
            "Function is not applicable for arguments",  //para's type or numbers is wrong
            "Not an array", //对非数组使用下标运算符：对int型变量或函数使用下标运算符
            "Not a function", //对变量使用函数调用：对变量使用函数调用运算符
            "The left-hand side of an assignment must be a variable" //赋值号左侧非变量或数组元素：对函数进行赋值操作
    };


    public static String toDec(String tokenText){
        if (tokenText.startsWith("0x") || tokenText.startsWith("0X")) {
            tokenText = String.valueOf(Integer.parseInt(tokenText.substring(2), 16));
        } else if (tokenText.startsWith("0")) {
            tokenText = String.valueOf(Integer.parseInt(tokenText, 8));
        }
        return tokenText;
    }


    public static void printSpaceDoubly(int a){
        for (int i = 0;i<a;i++){
            System.err.print("  ");
        }
    }

    public static String getErrorType(int typeNum){
        return errors[typeNum-1];
    }

    public static String getSpace(int num){
        String res = "";
        for (int i = 0;i<num;i++){
            res += "  ";
        }
        return res;
    }

    public static String getColor(String ruleName) {
        switch (ruleName) {
            case "CONST":
            case "INT":
            case "VOID":
            case "IF":
            case "ELSE":
            case "WHILE":
            case "BREAK":
            case "CONTINUE":
            case "RETURN": {
                return "orange";
            }

            case "PLUS":
            case "MINUS":
            case "MUL":
            case "DIV":
            case "MOD":
            case "ASSIGN":
            case "EQ":
            case "NEQ":
            case "LT":
            case "GT":
            case "LE":
            case "GE":
            case "NOT":
            case "AND":
            case "OR": {
                return "blue";
            }

            case "IDENT": {
                return "red";
            }

            case "INTEGER_CONST": {
                return "green";
            }

            default: {
                return "error";
            }
        }
    }

    public static boolean printError(int typeNum, int lineNum){
        System.out.println("Error type " + typeNum + " at Line " + lineNum + ": " + errors[typeNum-1] + ".");
        return true;
    }

    public static void helperArray(LLVMBuilderRef builder, int num_in_array, LLVMValueRef varPointer, LLVMValueRef[] initAr) {
        LLVMValueRef[] ap = new LLVMValueRef[2];
        ap[0] = LLVMConstInt(LLVMInt32Type(), 0, 0);
        for (int i = 0; i < num_in_array; i++) {
            ap[1] = LLVMConstInt(LLVMInt32Type(), i, 0);
            PointerPointer<LLVMValueRef> indexPointer = new PointerPointer<>(ap);
            LLVMBuildStore(builder, initAr[i], LLVMBuildGEP(builder, varPointer, indexPointer, 2, "" + i));
        }
    }
}
