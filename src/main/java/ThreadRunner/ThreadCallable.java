package ThreadRunner;

import java.util.concurrent.Callable;

public class ThreadCallable implements Callable<String>{
    private int threadNum;

    public ThreadCallable(int threadNum) {
        this.threadNum = threadNum;
    }

    @Override
    public String call() {

        try {
            Thread.sleep(threadNum);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println(Thread.currentThread().getName() + " finished");

        return Thread.currentThread().getName();
    }


}
