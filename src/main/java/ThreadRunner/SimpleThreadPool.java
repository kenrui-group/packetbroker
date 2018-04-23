package ThreadRunner;

import java.util.concurrent.*;
import java.util.*;

class SimpleThreadPool{
    public static void main(String args[])throws Exception{
        ExecutorService exec = new ThreadPoolExecutor(3, 3, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());

        List<CallableTask<String>> l1 = new ArrayList<> ();
        List<Future<String>> f1;

        /* Create 5 Callable Tasks */
        CallableTask<String> task1 = new CallableTask<> ("Task1");
        CallableTask<String> task2 = new CallableTask<> ("Task2");
        CallableTask<String> task3 = new CallableTask<> ("Task3");
        CallableTask<String> task4 = new CallableTask<> ("Task4");
        CallableTask<String> task5 = new CallableTask<> ("Task5");

        /* Adding the tasks to the list of type Callable */
        l1.add(task1);
        l1.add(task2);
        l1.add(task3);
        l1.add(task4);
        l1.add(task5);

        /*Submitting all the tasks in the list l1 to the Thread pool */
        try{
            f1 = exec.invokeAll(l1, 2000, TimeUnit.MILLISECONDS);
            exec.awaitTermination(3, TimeUnit.SECONDS);

            /* Priniting the resultes returned by the tasks */
            System.out.println("Are the tasks completed");
            for(Future obj : f1)
                System.out.println(obj.get() + "\t" +obj.isDone());
        }
        catch(Exception e){
            System.out.println("Exception is " +e);
        }
        exec.shutdown();
    }
}