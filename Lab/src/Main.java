import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import java.io.IOException;

public class Main {
    public static <Token> void main(String[] args) throws IOException {
        CharStream input = CharStreams.fromFileName(args[0]);
        SysYLexer sysYLexer = new SysYLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(sysYLexer);
        SysYParser sysYParser = new SysYParser(tokens);
        ParseTree tree = sysYParser.program();
        ParseTreeWalker walker = new ParseTreeWalker();
        TypeCheckListener listener = new TypeCheckListener();
        walker.walk(listener, tree);
        if (!listener.hasError) {
            for (Object o : listener.getMsgToPrint()){
                System.err.print(o);
            }
        }
    }

}


