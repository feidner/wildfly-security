package hfe.beans;

import javax.ejb.*;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

@Singleton
@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
@Lock(LockType.READ)
public class TransactionBean {

    public void runInNewTransaction(Runnable runnable) {
        runnable.run();
    }

    public <T> T produceInTransaction(Supplier<T> supplier) {
        return supplier.get();
    }

    public <V> V tryToProduceInNewTransaction(Callable<V> callable) throws Exception {
        return callable.call();
    }
}
