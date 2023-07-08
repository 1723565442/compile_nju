public class FunctionScope extends BaseScope{
    public FunctionScope(String name, Scope enclosingScope) {
        super(enclosingScope, name);
    }
}