import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.bytedeco.javacpp.BytePointer;

import java.io.IOException;

import static org.bytedeco.llvm.global.LLVM.*;


public class  Main {
    public static <Token> void main(String[] args) throws IOException {
        CharStream input = CharStreams.fromFileName(args[0]);
        SysYLexer sysYLexer = new SysYLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(sysYLexer);
        SysYParser sysYParser = new SysYParser(tokens);
        ParseTree tree = sysYParser.program();
        LlvmVisitor llvmVisitor = new LlvmVisitor();
        llvmVisitor.visit(tree);
        final BytePointer error = new BytePointer();
        String dest = "/home/qutx/Desktop/study/compile/Lab/nohup.out";
        if (LLVMPrintModuleToFile(llvmVisitor.module, args[1], error) != 0) {    // module是你自定义的LLVMModuleRef对象
            LLVMDisposeMessage(error);
        }

    }

}


