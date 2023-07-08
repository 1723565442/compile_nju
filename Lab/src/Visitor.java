import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

public class Visitor extends SysYParserBaseVisitor<Void> {
    private static int depth = 0;
    @Override
    public Void visitChildren(RuleNode node) {
        RuleContext text = node.getRuleContext();
        String ruleName = SysYParser.ruleNames[text.getRuleIndex()];
        String name = ruleName.substring(0, 1).toUpperCase() + ruleName.substring(1);
        Helper.printSpaceDoubly(depth);
        System.out.println(name);
        depth++;
        Void result = this.defaultResult();
        int n = node.getChildCount();
        for(int i = 0; i < n && this.shouldVisitNextChild(node, result); ++i) {
            ParseTree c = node.getChild(i);
            Void childResult = c.accept(this);
            result = this.aggregateResult(result, childResult);
        }
        depth--;
        return result;
    }

    @Override
    public Void visitTerminal(TerminalNode node) {
        Token token = node.getSymbol();
        int ruleNum = token.getType() - 1;
        if (ruleNum >= 0) {
            String ruleName = SysYLexer.ruleNames[ruleNum];
            String text = token.getText();
            String color = Helper.getColor(ruleName);
            if (ruleName.equals("INTEGER_CONST")) {
                text = Helper.toDec(text);
            }
            if (!color.equals("error")) {
                Helper.printSpaceDoubly(depth);
                System.out.println(text + " " + ruleName + "[" + color + "]");
            }
        }
        return null;
    }
}