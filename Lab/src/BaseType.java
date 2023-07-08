public class BaseType implements Type {
    public static BaseType typeInt = new BaseType("int");
    public static BaseType typeVoid = new BaseType("void");

    String name;

    private BaseType(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public static BaseType getTypeInt() {
        return typeInt;
    }

    public static BaseType getTypeVoid() {
        return typeVoid;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof BaseType) {
            return this.getName().equals(((BaseType) o).getName());
        }
        return false;
    }
}