public class GlobalScope extends BaseScope {

    public GlobalScope(Scope enclosingScope) {
        super("GlobalScope", enclosingScope);
        define(new BasicTypeSymbol(BaseType.getTypeInt()));
        define(new BasicTypeSymbol(BaseType.getTypeVoid()));
    }
}