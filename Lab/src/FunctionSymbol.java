public class FunctionSymbol extends BaseSymbol{
    public FunctionSymbol(FunctionType functionType) {
        super(functionType.getFunctionScope().getName(), functionType);
    }
}