package bguspl.set.ex;

import java.util.List;

public class BoundedQueue<T> {
    List<T> lst;
    int capacity;

    BoundedQueue(int capacity) {
        this.capacity = capacity;
    }

    public synchronized void add(T obj){
        
        while(lst.size() == capacity){
            try{
                this.wait();
            }
            catch(InterruptedException e) {}
        }

        lst.add(obj);
        this.notifyAll();
    }

    public synchronized T remove() {

        while(lst.size() == Table.INIT_INDEX){
            try{
                this.wait();
            }
            catch(InterruptedException e) {}
        }        

        T ret = lst.remove(Table.INIT_INDEX);
        this.notifyAll();
        return ret;
    }
}
