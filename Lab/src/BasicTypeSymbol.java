public class BasicTypeSymbol extends BaseSymbol {
    public BasicTypeSymbol(BaseType baseType) {
        super(baseType.getName(), baseType);
    }

    @Override
    public String toString() {
        return name;
    }
}