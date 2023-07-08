import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;


public class BaseScope implements Scope {
    private final Scope enclosingScope;
    private final Map<String, LLVMValueRef> refs = new LinkedHashMap<>();
    private String name;
    public HashMap<String, LLVMTypeRef> name_function_map = new HashMap<>();


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
    public void define(String name, LLVMValueRef llvmValueRef, LLVMTypeRef llvmTypeRef) {
        refs.put(name, llvmValueRef);
        name_function_map.put(name, llvmTypeRef);

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
    public LLVMTypeRef reolveType(String name) {
        LLVMTypeRef ret = name_function_map.get(name);
        if (null != ret) return  ret;
        if (null != enclosingScope){
            return enclosingScope.reolveType(name);
        }
        return null;
    }


    @Override
    public boolean definedSymbol(String name) {
        return refs.containsKey(name);
    }

}