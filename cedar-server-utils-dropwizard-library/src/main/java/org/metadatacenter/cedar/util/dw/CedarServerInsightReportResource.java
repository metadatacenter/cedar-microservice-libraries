package org.metadatacenter.cedar.util.dw;

import com.codahale.metrics.annotation.Timed;
import org.metadatacenter.exception.CedarException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.lang.management.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/insight")
@Produces(MediaType.APPLICATION_JSON)
public class CedarServerInsightReportResource {

  public CedarServerInsightReportResource() {
  }

  private void addMemory(Map<String, Object> r) {
    MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    r.put("jvm.memory.heap.used", memoryMXBean.getHeapMemoryUsage().getUsed());
    r.put("jvm.memory.heap.max", memoryMXBean.getHeapMemoryUsage().getMax());
    r.put("jvm.memory.heap.committed", memoryMXBean.getHeapMemoryUsage().getCommitted());
    r.put("jvm.memory.heap.init", memoryMXBean.getHeapMemoryUsage().getInit());

    r.put("jvm.memory.non-heap.used", memoryMXBean.getNonHeapMemoryUsage().getUsed());
    r.put("jvm.memory.non-heap.max", memoryMXBean.getNonHeapMemoryUsage().getMax());
    r.put("jvm.memory.non-heap.committed", memoryMXBean.getNonHeapMemoryUsage().getCommitted());
    r.put("jvm.memory.non-heap.init", memoryMXBean.getNonHeapMemoryUsage().getInit());

    Runtime runtime = Runtime.getRuntime();
    r.put("jvm.runtime.memory.total", runtime.totalMemory());
    r.put("jvm.runtime.memory.free", runtime.freeMemory());
    r.put("jvm.runtime.memory.max", runtime.maxMemory());
  }

  private void addSystem(Map<String, Object> r) {
    OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
    r.put("os.available-processors", operatingSystemMXBean.getAvailableProcessors());
    r.put("os.architecture", operatingSystemMXBean.getArch());
    r.put("os.name", operatingSystemMXBean.getName());
    r.put("os.version", operatingSystemMXBean.getVersion());
    r.put("os.load-average", operatingSystemMXBean.getSystemLoadAverage());
  }

  private void addThreads(Map<String, Object> r) {
    ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    r.put("current-thread.cpu-time", threadMXBean.getCurrentThreadCpuTime());
    r.put("current-thread.user-time", threadMXBean.getCurrentThreadUserTime());
    r.put("thread.count", threadMXBean.getThreadCount());
    r.put("thread.daemon.count", threadMXBean.getDaemonThreadCount());
    r.put("thread.started.count", threadMXBean.getTotalStartedThreadCount());
  }

  private void addGc(Map<String, Object> r) {
    for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
      r.put(b.getName(), b.getCollectionCount());
    }
  }

  private void addThreadDetails(Map<String, Object> r) {
    ThreadInfo[] threadInfos = ManagementFactory.getThreadMXBean().dumpAllThreads(true, true);
    Map<String, Object> threadInfo = new LinkedHashMap<>();
    for (ThreadInfo ti : threadInfos) {
      threadInfo.put("id", ti.getThreadId());
      threadInfo.put("name", ti.getThreadName());
      threadInfo.put("state", ti.getThreadState());
      threadInfo.put("priority", ti.getPriority());
      threadInfo.put("is-daemon", ti.isDaemon());
      threadInfo.put("is-in-native", ti.isInNative());
      threadInfo.put("is-suspended", ti.isSuspended());
      threadInfo.put("is-daemon", ti.isDaemon());

      Map<String, Object> li = null;
      LockInfo lockInfo = ti.getLockInfo();
      if (lockInfo != null) {
        li = new LinkedHashMap<>();
        li.put("class-name", lockInfo.getClassName());
        li.put("identity-hash", lockInfo.getIdentityHashCode());
      }
      threadInfo.put("lock-info", li);

      List<Object> monitorArray = new ArrayList<>();
      MonitorInfo[] lockedMonitors = ti.getLockedMonitors();
      for (MonitorInfo mi : lockedMonitors) {
        Map<String, Object> monitor = new LinkedHashMap<>();
        monitor.put("locked-stack-depth", mi.getLockedStackDepth());
        monitor.put("locked-stack-frame", mi.getLockedStackFrame());
        monitorArray.add(monitor);
      }
      threadInfo.put("monitors", monitorArray);

      List<Object> stackArray = new ArrayList<>();
      StackTraceElement[] stackTrace = ti.getStackTrace();
      for (StackTraceElement ste : stackTrace) {
        Map<String, Object> stack = new LinkedHashMap<>();
        stack.put("class-name", ste.getClassName());
        stack.put("method-name", ste.getMethodName());
        stack.put("file-name", ste.getFileName());
        stack.put("line-number", ste.getLineNumber());
        stackArray.add(stack);
      }
      threadInfo.put("stack-trace", stackArray);

      r.put(ti.getThreadName(), threadInfo);

    }
  }

  @GET
  @Timed
  @Path("/memory")
  public Response memory() throws CedarException {
    Map<String, Object> r = new LinkedHashMap<>();
    addMemory(r);
    return Response.ok().entity(r).build();
  }

  @GET
  @Timed
  @Path("/system")
  public Response system() throws CedarException {
    Map<String, Object> r = new LinkedHashMap<>();
    addSystem(r);
    return Response.ok().entity(r).build();
  }

  @GET
  @Timed
  @Path("/threads")
  public Response threads() throws CedarException {
    Map<String, Object> r = new LinkedHashMap<>();
    addThreads(r);
    return Response.ok().entity(r).build();
  }

  @GET
  @Timed
  @Path("/gc")
  public Response gc() throws CedarException {
    Map<String, Object> r = new LinkedHashMap<>();
    addGc(r);
    return Response.ok().entity(r).build();
  }

  @GET
  @Timed
  @Path("/thread-details")
  public Response threadDetails() throws CedarException {
    Map<String, Object> r = new LinkedHashMap<>();
    addThreadDetails(r);
    return Response.ok().entity(r).build();
  }

  @GET
  @Timed
  @Path("/full")
  public Response full() throws CedarException {
    Map<String, Object> r = new LinkedHashMap<>();
    addMemory(r);
    addSystem(r);
    addThreads(r);
    addGc(r);
    return Response.ok().entity(r).build();
  }

}