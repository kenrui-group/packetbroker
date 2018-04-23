package ThreadRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ThreadCaller {
    public static void main(String[] args) {
        List<ThreadCallable> taskList = new ArrayList<>();
        int threadRunTime = 1000;
        int threadCount = 3;
        int threadTimeOut = 2000;
        ExecutorService executorService;

        for (int i=1; i<10; i++) {
            taskList.add(new ThreadCallable(threadRunTime));
        }

        System.out.println("==============================================");

        executorService = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<String>> futureList = executorService.invokeAll(taskList, threadTimeOut, TimeUnit.MILLISECONDS);
            executorService.awaitTermination(2, TimeUnit.SECONDS);

            System.out.println("-------------------------------------------------");

            for (Future futureTask : futureList) {
                System.out.println(futureTask.get() + " " + futureTask.isDone());
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch(Exception e){
            System.out.println("Exception is " +e);
        }

        executorService.shutdown();

    }
}
