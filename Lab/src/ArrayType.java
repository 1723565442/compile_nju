public class ArrayType implements Type {
    Object count;
    Type type;

    public ArrayType(Object count, Type type) {
        this.count = count;
        this.type = type;
    }


    public Type getType() {
        return type;
    }

    @Override
    public String toString() {
        StringBuilder typeStr = new StringBuilder();
        if (count instanceof Integer && (int)count == 0) {
            return typeStr.append(type).toString();
        }
        return typeStr.append("array(")
                .append(count)
                .append(",")
                .append(type)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ArrayType) {
            return this.type.equals(((ArrayType) o).getType());
        }
        return false;
    }
}