package com.kareem.cortex;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import static org.junit.Assert.*;
public class CouncilExecutionLeaseTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void rejectsConcurrentOwnerAndAllowsReacquisition()throws Exception{
        File f=temp.newFile();
        try(CouncilExecutionLease first=CouncilExecutionLease.acquire(f)){
            assertNotNull(first);
            assertNull(CouncilExecutionLease.acquire(f));
        }
        try(CouncilExecutionLease next=CouncilExecutionLease.acquire(f)){assertNotNull(next);}
    }
    @Test public void exceptionalExitReleasesOwnership()throws Exception{
        File f=temp.newFile();
        try(CouncilExecutionLease first=CouncilExecutionLease.acquire(f)){
            assertNotNull(first);throw new IllegalStateException("setup failed");
        }catch(IllegalStateException expected){}
        try(CouncilExecutionLease next=CouncilExecutionLease.acquire(f)){assertNotNull(next);}
    }
}
