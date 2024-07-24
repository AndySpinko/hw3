package org.example;

import java.util.*;
import java.util.concurrent.*;

import static java.lang.String.join;


public class MultiThreadCrawler {

    public static void main(String[] args) throws Exception {

        MultiThreadCrawler multiThreadCrawler = new MultiThreadCrawler();

        long startTime = System.nanoTime();
        String result = multiThreadCrawler.find("Muse_(band)", "The_White_Stripes",20, 5, TimeUnit.MINUTES);
        long finishTime = TimeUnit.SECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);

        System.out.println("Took " + finishTime + " seconds, result is: " + result);
    }

    private final ExecutorService executorService = Executors.newFixedThreadPool(2000);
    private final Queue<Node> searchQueue = new ConcurrentLinkedQueue<>();
    private final Set<String> visited = ConcurrentHashMap.newKeySet();
    private final WikiClient client = new WikiClient();

    public String find(String from, String target, int deep, long timeout, TimeUnit timeUnit) throws Exception {
        long deadline = System.nanoTime() + timeUnit.toNanos(timeout);
        searchQueue.offer(new Node(from, null));

        Node finalResult = search(deadline, target, deep);

        if (finalResult.empty) {
            return "not found";
        }
        return getResult(finalResult);
    }

    private Node search(long deadline, String target, int deep) throws TimeoutException {
        while (!searchQueue.isEmpty()) {
            if (deadline < System.nanoTime()) {
                throw new TimeoutException();
            }

            var futures = new ArrayList<Future<Node>>();

            futures.add(executorService.submit(() -> {
                Node node = searchQueue.poll();
                System.out.println("Get page: " + node.title);

                Set<String> links = client.getByTitle(node.title);
                if (links.isEmpty()) {
                    //pageNotFound
                    return new Node();
                }
                for (String link : links) {
                    String currentLink = link.toLowerCase();
                    if (visited.add(currentLink)) {
                        Node subNode = new Node(link, node);
                        checkDeep(subNode, deep);
                        if (target.equalsIgnoreCase(currentLink)) {
                            return subNode;
                        }
                        searchQueue.offer(subNode);
                    }
                }
                return new Node();
            }));

            for (Future<Node> future : futures) {
                try {
                    if (!future.get().empty) {
                        executorService.shutdownNow();
                        return future.get();
                    }
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return new Node();
    }

    private String getResult(Node result) {
        var resultList = new ArrayList<String>();
        Node search = result;
        while (true) {
            resultList.add(search.title);
            if (search.next == null) {
                break;
            }
            search = search.next;
        }
        Collections.reverse(resultList);

        return join(" > ", resultList);
    }

    private void checkDeep(Node n, int deep) {
        int tmpDeep = 1;
        var forPrint = n;
        while (n.next != null) {
            n = n.next;
            tmpDeep++;
        }
        if (tmpDeep > deep) {
            System.out.println(getResult(forPrint));
            executorService.shutdownNow();
            throw new RuntimeException("Превышена глубина поиска");
        }
    }

    private static class Node {
        String title;
        Node next;
        Boolean empty;

        public Node() {
            this.empty = true;
        }

        public Node(String title, Node next) {
            this.title = title;
            this.next = next;
            this.empty = false;
        }
    }
}
