import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

import java.io.IOException;
import java.util.List;

public class Main
{
    public static <Token> void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("input path is required");
        }

        String source = args[0];
        CharStream input = CharStreams.fromFileName(source);
        SysYLexer sysYLexer = new SysYLexer(input);

        sysYLexer.removeErrorListeners();
        myErrorListener myErrorListener = new myErrorListener();
        sysYLexer.addErrorListener(myErrorListener);

        List<? extends org.antlr.v4.runtime.Token> tokens = sysYLexer.getAllTokens();

        if (myErrorListener.hasError){
            return;
        }

        String[] ruleNames = sysYLexer.getRuleNames();
        for (org.antlr.v4.runtime.Token token : tokens) {
            String tokenText = token.getText();
            int ruleNum = token.getType();
            int lineNum = token.getLine();
            if (ruleNum == 34)  tokenText = toDec(tokenText);
            System.err.printf("%s %s at Line %d.\n", ruleNames[ruleNum-1], tokenText, lineNum);
        }
    }
    public static String toDec(String a){
        if (a.startsWith("0x") || a.startsWith("0X")){
            return String.valueOf(Integer.parseInt(a.substring(2), 16));
        }else if (a.startsWith("0")){
            return String.valueOf(Integer.parseInt(a,8));
        }else return a;
    }

}


