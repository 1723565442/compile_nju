import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.LinkedHashMap;
import java.util.Map;


public class BaseScope implements Scope {
    private final Scope enclosingScope;
    private final Map<String, LLVMValueRef> refs = new LinkedHashMap<>();
    private String name;

    public BaseScope(Scope scope, String n){
        enclosingScope = scope;
        name = n;
    }


    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Scope getEnclosingScope() {
        return enclosingScope;
    }

    @Override
    public Map<String, LLVMValueRef> getRefs() {
        return refs;
    }

    @Override
    public void define(String name, LLVMValueRef llvmValueRef) {
        refs.put(name, llvmValueRef);

    }

    @Override
    public LLVMValueRef resolve(String name) {
        LLVMValueRef ret = refs.get(name);
        if (null != ret){
            return ret;
        }
        if (null != enclosingScope){
            return enclosingScope.resolve(name);
        }
        return null;
    }

    @Override
    public boolean definedSymbol(String name) {
        return refs.containsKey(name);
    }
}