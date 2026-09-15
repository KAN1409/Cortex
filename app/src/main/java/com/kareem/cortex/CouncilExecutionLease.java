package com.kareem.cortex;
import android.content.Context;
import java.io.*;
import java.nio.channels.*;
/** Kernel ownership is released on process death, not on Activity destruction. */
final class CouncilExecutionLease implements AutoCloseable {
    private final RandomAccessFile file;
    private final FileLock lock;
    private CouncilExecutionLease(RandomAccessFile f,FileLock l){file=f;lock=l;}
    static CouncilExecutionLease acquire(Context c)throws IOException{return acquire(new File(c.getFilesDir(),"council-execution.lock"));}
    static CouncilExecutionLease acquire(File path)throws IOException{
        RandomAccessFile f=new RandomAccessFile(path,"rw");
        try{FileLock l=f.getChannel().tryLock();if(l!=null)return new CouncilExecutionLease(f,l);}
        catch(OverlappingFileLockException busy){}
        catch(IOException|RuntimeException e){f.close();throw e;}
        f.close();return null;
    }
    public void close()throws IOException{try{lock.release();}finally{file.close();}}
}
