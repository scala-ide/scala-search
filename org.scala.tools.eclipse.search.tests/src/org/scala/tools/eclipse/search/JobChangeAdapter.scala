package org.scala.tools.eclipse.search

import org.eclipse.core.runtime.jobs.IJobChangeListener
import org.eclipse.core.runtime.jobs.IJobChangeEvent

class JobChangeAdapter extends IJobChangeListener {
  override def aboutToRun(event: IJobChangeEvent): Unit = {}
  override def awake(event: IJobChangeEvent): Unit = {}
  override def done(event: IJobChangeEvent): Unit = {}
  override def running(event: IJobChangeEvent): Unit = {}
  override def scheduled(event: IJobChangeEvent): Unit = {}
  override def sleeping(event: IJobChangeEvent): Unit = {}
}