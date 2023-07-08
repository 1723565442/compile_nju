import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.*;
import java.io.IOException;

public class Main {
    public static <Token> void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("input path is required");
        }

        String source =args[0];
        CharStream input = CharStreams.fromFileName(source);
        SysYLexer sysYLexer = new SysYLexer(input);

        CommonTokenStream tokens = new CommonTokenStream(sysYLexer);
        SysYParser sysYParser = new SysYParser(tokens);
        sysYParser.removeErrorListeners();
        myParserErrorListener myparserErrorListener = new myParserErrorListener();
        sysYParser.addErrorListener(myparserErrorListener);



        ParseTree tree = sysYParser.program();
        if (myparserErrorListener.hasParserError) {
            return;
        }

        //Visitor extends SysYParserBaseVisitor<Void>
        Visitor visitor = new Visitor();
        visitor.visit(tree);
    }
}


