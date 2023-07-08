import java.util.Map;

import org.bytedeco.llvm.LLVM.LLVMTypeRef;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

public interface Scope {
    String getName();

    void setName(String name);

    Scope getEnclosingScope();

    Map<String, LLVMValueRef> getRefs();

    void define(String name, LLVMValueRef llvmValueRef,LLVMTypeRef llvmTypeRef);


    public LLVMValueRef resolve(String name);

    public LLVMTypeRef reolveType(String name);

    boolean definedSymbol(String name);

}