package de.uni_koblenz.west.splendid;

import java.util.concurrent.atomic.AtomicInteger;

public class QueryInfo {
    public AtomicInteger nbAskQuery = new AtomicInteger();   
    public long planningTime = 0;
}
