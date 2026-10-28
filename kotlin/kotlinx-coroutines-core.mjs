import {
  Unit_instance14hm69wy3kr8u as Unit_instance,
  protoOf180f3jzyo7rfj as protoOf,
  THROW_CCE2g6jy02ryeudk as THROW_CCE,
  Element2gr7ezmxqaln7 as Element,
  Continuation1aa2oekvx7jm7 as Continuation,
  initMetadataForClassbxx6q50dy2s7 as initMetadataForClass,
  VOID7hggqo3abtya as VOID,
  EmptyCoroutineContext_getInstance2qfbu6z39urjq as EmptyCoroutineContext_getInstance,
  createCoroutineUnintercepted3gya308dmbbtg as createCoroutineUnintercepted,
  UnsupportedOperationException_init_$Create$1usmxm8d6ijjz as UnsupportedOperationException_init_$Create$,
  isInterface3d6p8outrmvmk as isInterface,
  toString1pkumu07cwy4m as toString,
  IllegalStateException_init_$Create$1nm9y6jd3c9iy as IllegalStateException_init_$Create$,
  toString30pk9tzaqopn as toString_0,
  get_COROUTINE_SUSPENDED3ujt3p13qm4iy as get_COROUTINE_SUSPENDED,
  initMetadataForInterface1egvbzx539z91 as initMetadataForInterface,
  initMetadataForObject1cxne3s9w65el as initMetadataForObject,
  hashCodeq5arwsb9dgti as hashCode,
  equals2au1ep9vhcato as equals,
  CancellationException_init_$Create$abblhb9ra5mk as CancellationException_init_$Create$,
  Result__exceptionOrNull_impl_p6xea93g9kuxe56cw2k as Result__exceptionOrNull_impl_p6xea9,
  _Result___get_value__impl__bjfvqgbxrwpgk5sesh as _Result___get_value__impl__bjfvqg,
  AbstractCoroutineContextKey9xr9r6wlj5bm as AbstractCoroutineContextKey,
  Key_instance10ebq86xndg52 as Key_instance,
  AbstractCoroutineContextElement2rpehg0hv5szw as AbstractCoroutineContextElement,
  getxe4seun860fg as get,
  minusKey2uxs00uz5ceqp as minusKey,
  ContinuationInterceptor2624y0vaqwxwf as ContinuationInterceptor,
  RuntimeException_init_$Create$1uo9cv0rggpzf as RuntimeException_init_$Create$,
  addSuppressedu5jwjfvsc039 as addSuppressed,
  Enum3alwj03lh1n41 as Enum,
  startCoroutine327fwvtqvedik as startCoroutine,
  noWhenBranchMatchedException2a6r7ubxgky5j as noWhenBranchMatchedException,
  Long2qws0ah9gnpki as Long,
  intercepted2ogpsikxxj4u0 as intercepted,
  ArrayDeque_init_$Create$3radqcftj9po2 as ArrayDeque_init_$Create$,
  RuntimeException1r3t0zl97011n as RuntimeException,
  RuntimeException_init_$Init$2bxjdc47ga271 as RuntimeException_init_$Init$,
  captureStack1fzi4aczwc4hg as captureStack,
  Error3ofk6owajcepa as Error_0,
  Error_init_$Init$2zp3hflsgcgv as Error_init_$Init$,
  StringBuilder_init_$Create$zzhi687lupah as StringBuilder_init_$Create$,
  throwUninitializedPropertyAccessExceptionyynx7gkm73wd as throwUninitializedPropertyAccessException,
  ArrayList_init_$Create$3m1kyiqequwm5 as ArrayList_init_$Create$,
  CancellationException3b36o9qz53rgr as CancellationException,
  ArrayList3it5z8td81qkl as ArrayList,
  IllegalStateException_init_$Create$1re2ox2t9owo2 as IllegalStateException_init_$Create$_0,
  plusolev77jfy5r9 as plus,
  get6d5x931vk0s as get_0,
  fold36i9psb7d5v48 as fold,
  minusKeyyqanvso9aovh as minusKey_0,
  anyToString3ho3k49fc56mj as anyToString,
  UnsupportedOperationException2tkumpmhredt3 as UnsupportedOperationException,
  Exceptiondt2hlxn7j7vw as Exception,
  IllegalArgumentException_init_$Create$sfel9vrfa7w2 as IllegalArgumentException_init_$Create$,
  Companion_instance1et3290wko23u as Companion_instance,
  createFailure8paxfkfa5dc7 as createFailure,
  _Result___init__impl__xyqfz8oelep8i0cbta as _Result___init__impl__xyqfz8,
  startCoroutineUninterceptedOrReturnNonGeneratorVersionyfrrvzbtl8bf as startCoroutineUninterceptedOrReturnNonGeneratorVersion,
  getKClassFromExpression3vpejubogshaw as getKClassFromExpression,
  removeFirstOrNull15yg2tczrh8a7 as removeFirstOrNull,
  KtList3hktaavzmj137 as KtList,
  MutableCollection3jqcg879qsnhr as MutableCollection,
  coerceIn302bduskdb54x as coerceIn,
  CancellationException_init_$Init$yjd67q19gh18 as CancellationException_init_$Init$,
  ensureNotNull1e947j3ixpazm as ensureNotNull,
  getStringHashCode26igk1bx568vk as getStringHashCode,
  HashSet_init_$Create$35v8jtxapyyka as HashSet_init_$Create$,
  RuntimeException_init_$Init$2i94smf8ys3t6 as RuntimeException_init_$Init$_0,
  LinkedHashSet_init_$Create$161uomhguzhxi as LinkedHashSet_init_$Create$,
} from './kotlin-kotlin-stdlib.mjs';
import {
  atomic$int$11d5swdyn6j0pu as atomic$int$1,
  atomic$ref$130aurmcwdfdf1 as atomic$ref$1,
  atomic$boolean$1iggki4z65a2h as atomic$boolean$1,
} from './kotlinx-atomicfu.mjs';
//region block: imports
var imul = Math.imul;
//endregion
//region block: pre-declaration
initMetadataForInterface(ParentJob, 'ParentJob', VOID, VOID, [Element], [0]);
initMetadataForClass(JobSupport, 'JobSupport', VOID, VOID, [Element, ParentJob], [0]);
initMetadataForInterface(CoroutineScope, 'CoroutineScope');
initMetadataForClass(AbstractCoroutine, 'AbstractCoroutine', VOID, JobSupport, [JobSupport, Element, Continuation, CoroutineScope], [0]);
initMetadataForClass(StandaloneCoroutine, 'StandaloneCoroutine', VOID, AbstractCoroutine, VOID, [0]);
initMetadataForClass(LazyStandaloneCoroutine, 'LazyStandaloneCoroutine', VOID, StandaloneCoroutine, VOID, [0]);
initMetadataForClass(DeferredCoroutine, 'DeferredCoroutine', VOID, AbstractCoroutine, [AbstractCoroutine, Element], [0]);
initMetadataForClass(LazyDeferredCoroutine, 'LazyDeferredCoroutine', VOID, DeferredCoroutine, VOID, [0]);
initMetadataForInterface(Runnable, 'Runnable');
initMetadataForClass(SchedulerTask, 'SchedulerTask', VOID, VOID, [Runnable]);
initMetadataForClass(DispatchedTask, 'DispatchedTask', VOID, SchedulerTask);
initMetadataForClass(CancellableContinuationImpl, 'CancellableContinuationImpl', VOID, DispatchedTask, [DispatchedTask, Continuation]);
initMetadataForInterface(NotCompleted, 'NotCompleted');
initMetadataForInterface(CancelHandler, 'CancelHandler', VOID, VOID, [NotCompleted]);
initMetadataForObject(Active, 'Active', VOID, VOID, [NotCompleted]);
initMetadataForClass(CompletedContinuation, 'CompletedContinuation');
initMetadataForClass(LockFreeLinkedListNode, 'LockFreeLinkedListNode', LockFreeLinkedListNode);
initMetadataForInterface(Incomplete, 'Incomplete');
initMetadataForClass(JobNode, 'JobNode', VOID, LockFreeLinkedListNode, [LockFreeLinkedListNode, Incomplete]);
initMetadataForClass(ChildContinuation, 'ChildContinuation', VOID, JobNode);
initMetadataForClass(CompletedExceptionally, 'CompletedExceptionally');
initMetadataForClass(CancelledContinuation, 'CancelledContinuation', VOID, CompletedExceptionally);
initMetadataForObject(Key, 'Key', VOID, AbstractCoroutineContextKey);
initMetadataForClass(CoroutineDispatcher, 'CoroutineDispatcher', VOID, AbstractCoroutineContextElement, [AbstractCoroutineContextElement, ContinuationInterceptor]);
initMetadataForObject(Key_0, 'Key');
initMetadataForObject(GlobalScope, 'GlobalScope', VOID, VOID, [CoroutineScope]);
initMetadataForClass(CoroutineStart, 'CoroutineStart', VOID, Enum);
initMetadataForInterface(Delay, 'Delay', VOID, VOID, VOID, [1]);
initMetadataForClass(EventLoop, 'EventLoop', VOID, CoroutineDispatcher);
initMetadataForObject(ThreadLocalEventLoop, 'ThreadLocalEventLoop');
initMetadataForClass(CompletionHandlerException, 'CompletionHandlerException', VOID, RuntimeException);
initMetadataForClass(CoroutinesInternalError, 'CoroutinesInternalError', VOID, Error_0);
initMetadataForObject(Key_1, 'Key');
initMetadataForObject(NonDisposableHandle, 'NonDisposableHandle');
initMetadataForClass(Empty, 'Empty', VOID, VOID, [Incomplete]);
initMetadataForClass(LockFreeLinkedListHead, 'LockFreeLinkedListHead', LockFreeLinkedListHead, LockFreeLinkedListNode);
initMetadataForClass(NodeList, 'NodeList', NodeList, LockFreeLinkedListHead, [LockFreeLinkedListHead, Incomplete]);
initMetadataForClass(SynchronizedObject, 'SynchronizedObject', SynchronizedObject);
initMetadataForClass(Finishing, 'Finishing', VOID, SynchronizedObject, [SynchronizedObject, Incomplete]);
initMetadataForClass(ChildCompletion, 'ChildCompletion', VOID, JobNode);
initMetadataForClass(InactiveNodeList, 'InactiveNodeList', VOID, VOID, [Incomplete]);
initMetadataForClass(InvokeOnCompletion, 'InvokeOnCompletion', VOID, JobNode);
initMetadataForClass(InvokeOnCancelling, 'InvokeOnCancelling', VOID, JobNode);
initMetadataForClass(ChildHandleNode, 'ChildHandleNode', VOID, JobNode);
initMetadataForClass(IncompleteStateBox, 'IncompleteStateBox');
initMetadataForClass(JobImpl, 'JobImpl', VOID, JobSupport, [JobSupport, Element], [0]);
initMetadataForClass(MainCoroutineDispatcher, 'MainCoroutineDispatcher', VOID, CoroutineDispatcher);
initMetadataForClass(SupervisorJobImpl, 'SupervisorJobImpl', VOID, JobImpl, VOID, [0]);
initMetadataForClass(TimeoutCancellationException, 'TimeoutCancellationException', VOID, CancellationException);
initMetadataForObject(Unconfined, 'Unconfined', VOID, CoroutineDispatcher);
initMetadataForObject(Key_2, 'Key');
initMetadataForClass(AbstractSharedFlow, 'AbstractSharedFlow', VOID, SynchronizedObject);
initMetadataForClass(StateFlowImpl, 'StateFlowImpl', VOID, AbstractSharedFlow, VOID, [1]);
initMetadataForClass(ConcurrentLinkedListNode, 'ConcurrentLinkedListNode');
initMetadataForClass(Segment, 'Segment', VOID, ConcurrentLinkedListNode, [ConcurrentLinkedListNode, NotCompleted]);
initMetadataForObject(ExceptionSuccessfullyProcessed, 'ExceptionSuccessfullyProcessed', VOID, Exception);
initMetadataForClass(DispatchedContinuation, 'DispatchedContinuation', VOID, DispatchedTask, [DispatchedTask, Continuation]);
initMetadataForClass(ContextScope, 'ContextScope', VOID, VOID, [CoroutineScope]);
initMetadataForClass(Symbol, 'Symbol');
initMetadataForClass(SetTimeoutBasedDispatcher, 'SetTimeoutBasedDispatcher', VOID, CoroutineDispatcher, [CoroutineDispatcher, Delay], [1]);
initMetadataForObject(NodeDispatcher, 'NodeDispatcher', VOID, SetTimeoutBasedDispatcher, VOID, [1]);
initMetadataForClass(MessageQueue, 'MessageQueue', VOID, VOID, [KtList, MutableCollection]);
initMetadataForClass(ScheduledMessageQueue, 'ScheduledMessageQueue', VOID, MessageQueue);
initMetadataForClass(WindowMessageQueue, 'WindowMessageQueue', VOID, MessageQueue);
initMetadataForClass(UnconfinedEventLoop, 'UnconfinedEventLoop', UnconfinedEventLoop, EventLoop);
initMetadataForObject(SetTimeoutDispatcher, 'SetTimeoutDispatcher', VOID, SetTimeoutBasedDispatcher, VOID, [1]);
initMetadataForClass(ClearTimeout, 'ClearTimeout', VOID, VOID, [CancelHandler]);
initMetadataForClass(WindowClearTimeout, 'WindowClearTimeout', VOID, ClearTimeout);
initMetadataForClass(WindowDispatcher, 'WindowDispatcher', VOID, CoroutineDispatcher, [CoroutineDispatcher, Delay], [1]);
initMetadataForObject(Dispatchers, 'Dispatchers');
initMetadataForClass(JsMainDispatcher, 'JsMainDispatcher', VOID, MainCoroutineDispatcher);
initMetadataForClass(JobCancellationException, 'JobCancellationException', VOID, CancellationException);
initMetadataForClass(DiagnosticCoroutineContextException, 'DiagnosticCoroutineContextException', VOID, RuntimeException);
initMetadataForClass(ListClosed, 'ListClosed', VOID, LockFreeLinkedListNode);
initMetadataForClass(CommonThreadLocal, 'CommonThreadLocal', CommonThreadLocal);
//endregion
function AbstractCoroutine(parentContext, initParentJob, active) {
  JobSupport.call(this, active);
  if (initParentJob) {
    this.o1f(parentContext.m9(Key_instance_2));
  }
  this.r1f_1 = parentContext.wg(this);
}
protoOf(AbstractCoroutine).f9 = function () {
  return this.r1f_1;
};
protoOf(AbstractCoroutine).s1f = function () {
  return this.r1f_1;
};
protoOf(AbstractCoroutine).t1f = function () {
  return protoOf(JobSupport).t1f.call(this);
};
protoOf(AbstractCoroutine).u1f = function (value) {
};
protoOf(AbstractCoroutine).v1f = function (cause, handled) {
};
protoOf(AbstractCoroutine).w1f = function () {
  return get_classSimpleName(this) + ' was cancelled';
};
protoOf(AbstractCoroutine).x1f = function (state) {
  if (state instanceof CompletedExceptionally) {
    this.v1f(state.y1f_1, state.a1g());
  } else {
    this.u1f((state == null ? true : !(state == null)) ? state : THROW_CCE());
  }
};
protoOf(AbstractCoroutine).k9 = function (result) {
  var state = this.b1g(toState_0(result));
  if (state === get_COMPLETING_WAITING_CHILDREN())
    return Unit_instance;
  this.c1g(state);
};
protoOf(AbstractCoroutine).c1g = function (state) {
  return this.d1g(state);
};
protoOf(AbstractCoroutine).e1g = function (exception) {
  handleCoroutineException(this.r1f_1, exception);
};
protoOf(AbstractCoroutine).f1g = function () {
  var tmp0_elvis_lhs = get_coroutineName(this.r1f_1);
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return protoOf(JobSupport).f1g.call(this);
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var coroutineName = tmp;
  return '"' + coroutineName + '":' + protoOf(JobSupport).f1g.call(this);
};
protoOf(AbstractCoroutine).g1g = function (start, receiver, block) {
  start.j1g(block, receiver, this);
};
function launch(_this__u8e3s4, context, start, block) {
  context = context === VOID ? EmptyCoroutineContext_getInstance() : context;
  start = start === VOID ? CoroutineStart_DEFAULT_getInstance() : start;
  var newContext = newCoroutineContext(_this__u8e3s4, context);
  var coroutine = start.k1h() ? new LazyStandaloneCoroutine(newContext, block) : new StandaloneCoroutine(newContext, true);
  coroutine.g1g(start, coroutine, block);
  return coroutine;
}
function StandaloneCoroutine(parentContext, active) {
  AbstractCoroutine.call(this, parentContext, true, active);
}
protoOf(StandaloneCoroutine).g1h = function (exception) {
  handleCoroutineException(this.r1f_1, exception);
  return true;
};
function LazyStandaloneCoroutine(parentContext, block) {
  StandaloneCoroutine.call(this, parentContext, false);
  this.r1h_1 = createCoroutineUnintercepted(block, this, this);
}
protoOf(LazyStandaloneCoroutine).p1g = function () {
  startCoroutineCancellable(this.r1h_1, this);
};
function async(_this__u8e3s4, context, start, block) {
  context = context === VOID ? EmptyCoroutineContext_getInstance() : context;
  start = start === VOID ? CoroutineStart_DEFAULT_getInstance() : start;
  var newContext = newCoroutineContext(_this__u8e3s4, context);
  var coroutine = start.k1h() ? new LazyDeferredCoroutine(newContext, block) : new DeferredCoroutine(newContext, true);
  coroutine.g1g(start, coroutine, block);
  return coroutine;
}
function DeferredCoroutine(parentContext, active) {
  AbstractCoroutine.call(this, parentContext, true, active);
}
protoOf(DeferredCoroutine).v1h = function () {
  var tmp = this.j1h();
  return (tmp == null ? true : !(tmp == null)) ? tmp : THROW_CCE();
};
function LazyDeferredCoroutine(parentContext, block) {
  DeferredCoroutine.call(this, parentContext, false);
  this.z1h_1 = createCoroutineUnintercepted(block, this, this);
}
protoOf(LazyDeferredCoroutine).p1g = function () {
  startCoroutineCancellable(this.z1h_1, this);
};
function invokeOnCancellation(_this__u8e3s4, handler) {
  var tmp;
  if (_this__u8e3s4 instanceof CancellableContinuationImpl) {
    _this__u8e3s4.g1i(handler);
    tmp = Unit_instance;
  } else {
    throw UnsupportedOperationException_init_$Create$('third-party implementation of CancellableContinuation is not supported');
  }
  return tmp;
}
function _get_parentHandle__f8dcex($this) {
  return $this.f1i_1.kotlinx$atomicfu$value;
}
function _get_stateDebugRepresentation__bf18u4($this) {
  var tmp5_subject = $this.m1g();
  var tmp;
  if (!(tmp5_subject == null) ? isInterface(tmp5_subject, NotCompleted) : false) {
    tmp = 'Active';
  } else {
    if (tmp5_subject instanceof CancelledContinuation) {
      tmp = 'Cancelled';
    } else {
      tmp = 'Completed';
    }
  }
  return tmp;
}
function isReusable($this) {
  var tmp;
  if (get_isReusableMode($this.o1i_1)) {
    var tmp_0 = $this.b1i_1;
    tmp = (tmp_0 instanceof DispatchedContinuation ? tmp_0 : THROW_CCE()).n1i();
  } else {
    tmp = false;
  }
  return tmp;
}
function cancelLater($this, cause) {
  if (!isReusable($this))
    return false;
  var tmp = $this.b1i_1;
  var dispatched = tmp instanceof DispatchedContinuation ? tmp : THROW_CCE();
  return dispatched.p1i(cause);
}
function callSegmentOnCancellation($this, segment, cause) {
  // Inline function 'kotlinx.coroutines.index' call
  var index = $this.d1i_1.kotlinx$atomicfu$value & 536870911;
  // Inline function 'kotlin.check' call
  if (!!(index === 536870911)) {
    // Inline function 'kotlinx.coroutines.CancellableContinuationImpl.callSegmentOnCancellation.<anonymous>' call
    var message = 'The index for Segment.onCancellation(..) is broken';
    throw IllegalStateException_init_$Create$(toString(message));
  }
  // Inline function 'kotlinx.coroutines.CancellableContinuationImpl.callCancelHandlerSafely' call
  try {
    // Inline function 'kotlinx.coroutines.CancellableContinuationImpl.callSegmentOnCancellation.<anonymous>' call
    segment.q1i(index, cause, $this.f9());
  } catch ($p) {
    if ($p instanceof Error) {
      var ex = $p;
      handleCoroutineException($this.f9(), new CompletionHandlerException('Exception in invokeOnCancellation handler for ' + $this.toString(), ex));
    } else {
      throw $p;
    }
  }
}
function trySuspend($this) {
  // Inline function 'kotlinx.atomicfu.loop' call
  var this_0 = $this.d1i_1;
  while (true) {
    // Inline function 'kotlinx.coroutines.CancellableContinuationImpl.trySuspend.<anonymous>' call
    var cur = this_0.kotlinx$atomicfu$value;
    // Inline function 'kotlinx.coroutines.decision' call
    switch (cur >> 29) {
      case 0:
        // Inline function 'kotlinx.coroutines.index' call

        // Inline function 'kotlinx.coroutines.decisionAndIndex' call

        var tmp$ret$2 = (1 << 29) + (cur & 536870911) | 0;
        if ($this.d1i_1.atomicfu$compareAndSet(cur, tmp$ret$2))
          return true;
        break;
      case 2:
        return false;
      default:
        // Inline function 'kotlin.error' call

        var message = 'Already suspended';
        throw IllegalStateException_init_$Create$(toString(message));
    }
  }
}
function tryResume($this) {
  // Inline function 'kotlinx.atomicfu.loop' call
  var this_0 = $this.d1i_1;
  while (true) {
    // Inline function 'kotlinx.coroutines.CancellableContinuationImpl.tryResume.<anonymous>' call
    var cur = this_0.kotlinx$atomicfu$value;
    // Inline function 'kotlinx.coroutines.decision' call
    switch (cur >> 29) {
      case 0:
        // Inline function 'kotlinx.coroutines.index' call

        // Inline function 'kotlinx.coroutines.decisionAndIndex' call

        var tmp$ret$2 = (2 << 29) + (cur & 536870911) | 0;
        if ($this.d1i_1.atomicfu$compareAndSet(cur, tmp$ret$2))
          return true;
        break;
      case 1:
        return false;
      default:
        // Inline function 'kotlin.error' call

        var message = 'Already resumed';
        throw IllegalStateException_init_$Create$(toString(message));
    }
  }
}
function installParentHandle($this) {
  var tmp0_elvis_lhs = $this.f9().m9(Key_instance_2);
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return null;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var parent = tmp;
  var handle = invokeOnCompletion(parent, VOID, new ChildContinuation($this));
  $this.f1i_1.atomicfu$compareAndSet(null, handle);
  return handle;
}
function invokeOnCancellationImpl($this, handler) {
  // Inline function 'kotlinx.coroutines.assert' call
  // Inline function 'kotlinx.atomicfu.loop' call
  var this_0 = $this.e1i_1;
  while (true) {
    // Inline function 'kotlinx.coroutines.CancellableContinuationImpl.invokeOnCancellationImpl.<anonymous>' call
    var state = this_0.kotlinx$atomicfu$value;
    if (state instanceof Active) {
      if ($this.e1i_1.atomicfu$compareAndSet(state, handler))
        return Unit_instance;
    } else {
      var tmp;
      if (!(state == null) ? isInterface(state, CancelHandler) : false) {
        tmp = true;
      } else {
        tmp = state instanceof Segment;
      }
      if (tmp) {
        multipleHandlersError($this, handler, state);
      } else {
        if (state instanceof CompletedExceptionally) {
          if (!state.z1i()) {
            multipleHandlersError($this, handler, state);
          }
          if (state instanceof CancelledContinuation) {
            var tmp13_safe_receiver = state instanceof CompletedExceptionally ? state : null;
            var cause = tmp13_safe_receiver == null ? null : tmp13_safe_receiver.y1f_1;
            if (isInterface(handler, CancelHandler)) {
              $this.w1i(handler, cause);
            } else {
              var segment = handler instanceof Segment ? handler : THROW_CCE();
              callSegmentOnCancellation($this, segment, cause);
            }
          }
          return Unit_instance;
        } else {
          if (state instanceof CompletedContinuation) {
            if (!(state.s1i_1 == null)) {
              multipleHandlersError($this, handler, state);
            }
            if (handler instanceof Segment)
              return Unit_instance;
            if (!isInterface(handler, CancelHandler))
              THROW_CCE();
            if (state.x1i()) {
              $this.w1i(handler, state.v1i_1);
              return Unit_instance;
            }
            var update = state.y1i(VOID, handler);
            if ($this.e1i_1.atomicfu$compareAndSet(state, update))
              return Unit_instance;
          } else {
            if (handler instanceof Segment)
              return Unit_instance;
            if (!isInterface(handler, CancelHandler))
              THROW_CCE();
            var update_0 = new CompletedContinuation(state, handler);
            if ($this.e1i_1.atomicfu$compareAndSet(state, update_0))
              return Unit_instance;
          }
        }
      }
    }
  }
}
function multipleHandlersError($this, handler, state) {
  // Inline function 'kotlin.error' call
  var message = "It's prohibited to register multiple handlers, tried to register " + toString(handler) + ', already has ' + toString_0(state);
  throw IllegalStateException_init_$Create$(toString(message));
}
function dispatchResume($this, mode) {
  if (tryResume($this))
    return Unit_instance;
  dispatch($this, mode);
}
function resumedState($this, state, proposedUpdate, resumeMode, onCancellation, idempotent) {
  var tmp;
  if (proposedUpdate instanceof CompletedExceptionally) {
    // Inline function 'kotlinx.coroutines.assert' call
    // Inline function 'kotlinx.coroutines.assert' call
    tmp = proposedUpdate;
  } else {
    if (!get_isCancellableMode(resumeMode) && idempotent == null) {
      tmp = proposedUpdate;
    } else {
      var tmp_0;
      var tmp_1;
      if (!(onCancellation == null)) {
        tmp_1 = true;
      } else {
        tmp_1 = isInterface(state, CancelHandler);
      }
      if (tmp_1) {
        tmp_0 = true;
      } else {
        tmp_0 = !(idempotent == null);
      }
      if (tmp_0) {
        tmp = new CompletedContinuation(proposedUpdate, isInterface(state, CancelHandler) ? state : null, onCancellation, idempotent);
      } else {
        tmp = proposedUpdate;
      }
    }
  }
  return tmp;
}
function alreadyResumedError($this, proposedUpdate) {
  // Inline function 'kotlin.error' call
  var message = 'Already resumed, but proposed with update ' + toString_0(proposedUpdate);
  throw IllegalStateException_init_$Create$(toString(message));
}
function detachChildIfNonResuable($this) {
  if (!isReusable($this)) {
    $this.a1j();
  }
}
function CancellableContinuationImpl(delegate, resumeMode) {
  DispatchedTask.call(this, resumeMode);
  this.b1i_1 = delegate;
  // Inline function 'kotlinx.coroutines.assert' call
  this.c1i_1 = this.b1i_1.f9();
  var tmp = this;
  // Inline function 'kotlinx.coroutines.decisionAndIndex' call
  var tmp$ret$0 = (0 << 29) + 536870911 | 0;
  tmp.d1i_1 = atomic$int$1(tmp$ret$0);
  this.e1i_1 = atomic$ref$1(Active_instance);
  this.f1i_1 = atomic$ref$1(null);
}
protoOf(CancellableContinuationImpl).b1j = function () {
  return this.b1i_1;
};
protoOf(CancellableContinuationImpl).f9 = function () {
  return this.c1i_1;
};
protoOf(CancellableContinuationImpl).m1g = function () {
  return this.e1i_1.kotlinx$atomicfu$value;
};
protoOf(CancellableContinuationImpl).n1g = function () {
  var tmp = this.m1g();
  return !(!(tmp == null) ? isInterface(tmp, NotCompleted) : false);
};
protoOf(CancellableContinuationImpl).c1j = function () {
  var tmp0_elvis_lhs = installParentHandle(this);
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return Unit_instance;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var handle = tmp;
  if (this.n1g()) {
    handle.d1j();
    this.f1i_1.kotlinx$atomicfu$value = NonDisposableHandle_instance;
  }
};
protoOf(CancellableContinuationImpl).e1j = function () {
  return this.m1g();
};
protoOf(CancellableContinuationImpl).f1j = function (takenState, cause) {
  var this_0 = this.e1i_1;
  while (true) {
    // Inline function 'kotlinx.coroutines.CancellableContinuationImpl.cancelCompletedResult.<anonymous>' call
    var state = this_0.kotlinx$atomicfu$value;
    if (!(state == null) ? isInterface(state, NotCompleted) : false) {
      // Inline function 'kotlin.error' call
      var message = 'Not completed';
      throw IllegalStateException_init_$Create$(toString(message));
    } else {
      if (state instanceof CompletedExceptionally)
        return Unit_instance;
      else {
        if (state instanceof CompletedContinuation) {
          // Inline function 'kotlin.check' call
          if (!!state.x1i()) {
            // Inline function 'kotlinx.coroutines.CancellableContinuationImpl.cancelCompletedResult.<anonymous>.<anonymous>' call
            var message_0 = 'Must be called at most once';
            throw IllegalStateException_init_$Create$(toString(message_0));
          }
          var update = state.y1i(VOID, VOID, VOID, VOID, cause);
          if (this.e1i_1.atomicfu$compareAndSet(state, update)) {
            state.g1j(this, cause);
            return Unit_instance;
          }
        } else {
          if (this.e1i_1.atomicfu$compareAndSet(state, new CompletedContinuation(state, VOID, VOID, VOID, cause))) {
            return Unit_instance;
          }
        }
      }
    }
  }
  return Unit_instance;
};
protoOf(CancellableContinuationImpl).h1j = function (cause) {
  // Inline function 'kotlinx.atomicfu.loop' call
  var this_0 = this.e1i_1;
  while (true) {
    var tmp1 = this_0.kotlinx$atomicfu$value;
    $l$block: {
      // Inline function 'kotlinx.coroutines.CancellableContinuationImpl.cancel.<anonymous>' call
      if (!(!(tmp1 == null) ? isInterface(tmp1, NotCompleted) : false))
        return false;
      var tmp;
      if (isInterface(tmp1, CancelHandler)) {
        tmp = true;
      } else {
        tmp = tmp1 instanceof Segment;
      }
      var update = new CancelledContinuation(this, cause, tmp);
      if (!this.e1i_1.atomicfu$compareAndSet(tmp1, update)) {
        break $l$block;
      }
      if (isInterface(tmp1, CancelHandler)) {
        this.w1i(tmp1, cause);
      } else {
        if (tmp1 instanceof Segment) {
          callSegmentOnCancellation(this, tmp1, cause);
        }
      }
      detachChildIfNonResuable(this);
      dispatchResume(this, this.o1i_1);
      return true;
    }
  }
};
protoOf(CancellableContinuationImpl).i1j = function (cause) {
  if (cancelLater(this, cause))
    return Unit_instance;
  this.h1j(cause);
  detachChildIfNonResuable(this);
};
protoOf(CancellableContinuationImpl).w1i = function (handler, cause) {
  var tmp;
  try {
    handler.j1j(cause);
    tmp = Unit_instance;
  } catch ($p) {
    var tmp_0;
    if ($p instanceof Error) {
      var ex = $p;
      handleCoroutineException(this.f9(), new CompletionHandlerException('Exception in invokeOnCancellation handler for ' + this.toString(), ex));
      tmp_0 = Unit_instance;
    } else {
      throw $p;
    }
    tmp = tmp_0;
  }
  return tmp;
};
protoOf(CancellableContinuationImpl).k1j = function (onCancellation, cause, value) {
  try {
    onCancellation(cause, value, this.f9());
  } catch ($p) {
    if ($p instanceof Error) {
      var ex = $p;
      handleCoroutineException(this.f9(), new CompletionHandlerException('Exception in resume onCancellation handler for ' + this.toString(), ex));
    } else {
      throw $p;
    }
  }
};
protoOf(CancellableContinuationImpl).l1j = function (parent) {
  return parent.q1g();
};
protoOf(CancellableContinuationImpl).m1j = function () {
  var isReusable_0 = isReusable(this);
  if (trySuspend(this)) {
    if (_get_parentHandle__f8dcex(this) == null) {
      installParentHandle(this);
    }
    if (isReusable_0) {
      this.n1j();
    }
    return get_COROUTINE_SUSPENDED();
  }
  if (isReusable_0) {
    this.n1j();
  }
  var state = this.m1g();
  if (state instanceof CompletedExceptionally)
    throw recoverStackTrace(state.y1f_1, this);
  if (get_isCancellableMode(this.o1i_1)) {
    var job = this.f9().m9(Key_instance_2);
    if (!(job == null) && !job.t1f()) {
      var cause = job.q1g();
      this.f1j(state, cause);
      throw recoverStackTrace(cause, this);
    }
  }
  return this.o1j(state);
};
protoOf(CancellableContinuationImpl).n1j = function () {
  var tmp = this.b1i_1;
  var tmp10_safe_receiver = tmp instanceof DispatchedContinuation ? tmp : null;
  var tmp0_elvis_lhs = tmp10_safe_receiver == null ? null : tmp10_safe_receiver.p1j(this);
  var tmp_0;
  if (tmp0_elvis_lhs == null) {
    return Unit_instance;
  } else {
    tmp_0 = tmp0_elvis_lhs;
  }
  var cancellationCause = tmp_0;
  this.a1j();
  this.h1j(cancellationCause);
};
protoOf(CancellableContinuationImpl).k9 = function (result) {
  return this.q1j(toState(result, this), this.o1i_1);
};
protoOf(CancellableContinuationImpl).g1i = function (handler) {
  return invokeOnCancellationImpl(this, handler);
};
protoOf(CancellableContinuationImpl).r1j = function (proposedUpdate, resumeMode, onCancellation) {
  // Inline function 'kotlinx.atomicfu.loop' call
  var this_0 = this.e1i_1;
  while (true) {
    var tmp1 = this_0.kotlinx$atomicfu$value;
    $l$block: {
      // Inline function 'kotlinx.coroutines.CancellableContinuationImpl.resumeImpl.<anonymous>' call
      if (!(tmp1 == null) ? isInterface(tmp1, NotCompleted) : false) {
        var update = resumedState(this, tmp1, proposedUpdate, resumeMode, onCancellation, null);
        if (!this.e1i_1.atomicfu$compareAndSet(tmp1, update)) {
          break $l$block;
        }
        detachChildIfNonResuable(this);
        dispatchResume(this, resumeMode);
        return Unit_instance;
      } else {
        if (tmp1 instanceof CancelledContinuation) {
          if (tmp1.v1j()) {
            if (onCancellation == null)
              null;
            else {
              // Inline function 'kotlin.let' call
              // Inline function 'kotlinx.coroutines.CancellableContinuationImpl.resumeImpl.<anonymous>.<anonymous>' call
              this.k1j(onCancellation, tmp1.y1f_1, proposedUpdate);
            }
            return Unit_instance;
          }
        }
      }
      alreadyResumedError(this, proposedUpdate);
    }
  }
};
protoOf(CancellableContinuationImpl).q1j = function (proposedUpdate, resumeMode, onCancellation, $super) {
  onCancellation = onCancellation === VOID ? null : onCancellation;
  var tmp;
  if ($super === VOID) {
    this.r1j(proposedUpdate, resumeMode, onCancellation);
    tmp = Unit_instance;
  } else {
    tmp = $super.r1j.call(this, proposedUpdate, resumeMode, onCancellation);
  }
  return tmp;
};
protoOf(CancellableContinuationImpl).a1j = function () {
  var tmp0_elvis_lhs = _get_parentHandle__f8dcex(this);
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return Unit_instance;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var handle = tmp;
  handle.d1j();
  this.f1i_1.kotlinx$atomicfu$value = NonDisposableHandle_instance;
};
protoOf(CancellableContinuationImpl).w1j = function (_this__u8e3s4, value) {
  var tmp = this.b1i_1;
  var dc = tmp instanceof DispatchedContinuation ? tmp : null;
  var tmp_0;
  if ((dc == null ? null : dc.i1i_1) === _this__u8e3s4) {
    tmp_0 = 4;
  } else {
    tmp_0 = this.o1i_1;
  }
  this.q1j(value, tmp_0);
};
protoOf(CancellableContinuationImpl).o1j = function (state) {
  var tmp;
  if (state instanceof CompletedContinuation) {
    var tmp_0 = state.r1i_1;
    tmp = (tmp_0 == null ? true : !(tmp_0 == null)) ? tmp_0 : THROW_CCE();
  } else {
    tmp = (state == null ? true : !(state == null)) ? state : THROW_CCE();
  }
  return tmp;
};
protoOf(CancellableContinuationImpl).x1j = function (state) {
  var tmp20_safe_receiver = protoOf(DispatchedTask).x1j.call(this, state);
  var tmp;
  if (tmp20_safe_receiver == null) {
    tmp = null;
  } else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlinx.coroutines.CancellableContinuationImpl.getExceptionalResult.<anonymous>' call
    tmp = recoverStackTrace(tmp20_safe_receiver, this.b1i_1);
  }
  return tmp;
};
protoOf(CancellableContinuationImpl).toString = function () {
  return this.y1j() + '(' + toDebugString(this.b1i_1) + '){' + _get_stateDebugRepresentation__bf18u4(this) + '}@' + get_hexAddress(this);
};
protoOf(CancellableContinuationImpl).y1j = function () {
  return 'CancellableContinuation';
};
function NotCompleted() {
}
function CancelHandler() {
}
function Active() {
}
protoOf(Active).toString = function () {
  return 'Active';
};
var Active_instance;
function Active_getInstance() {
  return Active_instance;
}
function CompletedContinuation(result, cancelHandler, onCancellation, idempotentResume, cancelCause) {
  cancelHandler = cancelHandler === VOID ? null : cancelHandler;
  onCancellation = onCancellation === VOID ? null : onCancellation;
  idempotentResume = idempotentResume === VOID ? null : idempotentResume;
  cancelCause = cancelCause === VOID ? null : cancelCause;
  this.r1i_1 = result;
  this.s1i_1 = cancelHandler;
  this.t1i_1 = onCancellation;
  this.u1i_1 = idempotentResume;
  this.v1i_1 = cancelCause;
}
protoOf(CompletedContinuation).x1i = function () {
  return !(this.v1i_1 == null);
};
protoOf(CompletedContinuation).g1j = function (cont, cause) {
  var tmp21_safe_receiver = this.s1i_1;
  if (tmp21_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlinx.coroutines.CompletedContinuation.invokeHandlers.<anonymous>' call
    cont.w1i(tmp21_safe_receiver, cause);
  }
  var tmp22_safe_receiver = this.t1i_1;
  if (tmp22_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlinx.coroutines.CompletedContinuation.invokeHandlers.<anonymous>' call
    cont.k1j(tmp22_safe_receiver, cause, this.r1i_1);
  }
};
protoOf(CompletedContinuation).b1k = function (result, cancelHandler, onCancellation, idempotentResume, cancelCause) {
  return new CompletedContinuation(result, cancelHandler, onCancellation, idempotentResume, cancelCause);
};
protoOf(CompletedContinuation).y1i = function (result, cancelHandler, onCancellation, idempotentResume, cancelCause, $super) {
  result = result === VOID ? this.r1i_1 : result;
  cancelHandler = cancelHandler === VOID ? this.s1i_1 : cancelHandler;
  onCancellation = onCancellation === VOID ? this.t1i_1 : onCancellation;
  idempotentResume = idempotentResume === VOID ? this.u1i_1 : idempotentResume;
  cancelCause = cancelCause === VOID ? this.v1i_1 : cancelCause;
  return $super === VOID ? this.b1k(result, cancelHandler, onCancellation, idempotentResume, cancelCause) : $super.b1k.call(this, result, cancelHandler, onCancellation, idempotentResume, cancelCause);
};
protoOf(CompletedContinuation).toString = function () {
  return 'CompletedContinuation(result=' + toString_0(this.r1i_1) + ', cancelHandler=' + toString_0(this.s1i_1) + ', onCancellation=' + toString_0(this.t1i_1) + ', idempotentResume=' + toString_0(this.u1i_1) + ', cancelCause=' + toString_0(this.v1i_1) + ')';
};
protoOf(CompletedContinuation).hashCode = function () {
  var result = this.r1i_1 == null ? 0 : hashCode(this.r1i_1);
  result = imul(result, 31) + (this.s1i_1 == null ? 0 : hashCode(this.s1i_1)) | 0;
  result = imul(result, 31) + (this.t1i_1 == null ? 0 : hashCode(this.t1i_1)) | 0;
  result = imul(result, 31) + (this.u1i_1 == null ? 0 : hashCode(this.u1i_1)) | 0;
  result = imul(result, 31) + (this.v1i_1 == null ? 0 : hashCode(this.v1i_1)) | 0;
  return result;
};
protoOf(CompletedContinuation).equals = function (other) {
  if (this === other)
    return true;
  if (!(other instanceof CompletedContinuation))
    return false;
  var tmp0_other_with_cast = other instanceof CompletedContinuation ? other : THROW_CCE();
  if (!equals(this.r1i_1, tmp0_other_with_cast.r1i_1))
    return false;
  if (!equals(this.s1i_1, tmp0_other_with_cast.s1i_1))
    return false;
  if (!equals(this.t1i_1, tmp0_other_with_cast.t1i_1))
    return false;
  if (!equals(this.u1i_1, tmp0_other_with_cast.u1i_1))
    return false;
  if (!equals(this.v1i_1, tmp0_other_with_cast.v1i_1))
    return false;
  return true;
};
function ChildContinuation(child) {
  JobNode.call(this);
  this.g1k_1 = child;
}
protoOf(ChildContinuation).h1k = function () {
  return true;
};
protoOf(ChildContinuation).j1j = function (cause) {
  this.g1k_1.i1j(this.g1k_1.l1j(this.m1k()));
};
function CompletedExceptionally(cause, handled) {
  handled = handled === VOID ? false : handled;
  this.y1f_1 = cause;
  this.z1f_1 = atomic$boolean$1(handled);
}
protoOf(CompletedExceptionally).a1g = function () {
  return this.z1f_1.kotlinx$atomicfu$value;
};
protoOf(CompletedExceptionally).z1i = function () {
  return this.z1f_1.atomicfu$compareAndSet(false, true);
};
protoOf(CompletedExceptionally).toString = function () {
  return get_classSimpleName(this) + '[' + this.y1f_1.toString() + ']';
};
function CancelledContinuation(continuation, cause, handled) {
  CompletedExceptionally.call(this, cause == null ? CancellationException_init_$Create$('Continuation ' + toString(continuation) + ' was cancelled normally') : cause, handled);
  this.u1j_1 = atomic$boolean$1(false);
}
protoOf(CancelledContinuation).v1j = function () {
  return this.u1j_1.atomicfu$compareAndSet(false, true);
};
function toState(_this__u8e3s4, caller) {
  // Inline function 'kotlin.getOrElse' call
  var exception = Result__exceptionOrNull_impl_p6xea9(_this__u8e3s4);
  var tmp;
  if (exception == null) {
    var tmp_0 = _Result___get_value__impl__bjfvqg(_this__u8e3s4);
    tmp = (tmp_0 == null ? true : !(tmp_0 == null)) ? tmp_0 : THROW_CCE();
  } else {
    // Inline function 'kotlinx.coroutines.toState.<anonymous>' call
    tmp = new CompletedExceptionally(recoverStackTrace(exception, caller));
  }
  return tmp;
}
function toState_0(_this__u8e3s4) {
  // Inline function 'kotlin.getOrElse' call
  var exception = Result__exceptionOrNull_impl_p6xea9(_this__u8e3s4);
  var tmp;
  if (exception == null) {
    var tmp_0 = _Result___get_value__impl__bjfvqg(_this__u8e3s4);
    tmp = (tmp_0 == null ? true : !(tmp_0 == null)) ? tmp_0 : THROW_CCE();
  } else {
    // Inline function 'kotlinx.coroutines.toState.<anonymous>' call
    tmp = new CompletedExceptionally(exception);
  }
  return tmp;
}
function CoroutineDispatcher$Key$_init_$lambda_akl8b5(it) {
  return it instanceof CoroutineDispatcher ? it : null;
}
function Key() {
  Key_instance_0 = this;
  var tmp = Key_instance;
  AbstractCoroutineContextKey.call(this, tmp, CoroutineDispatcher$Key$_init_$lambda_akl8b5);
}
var Key_instance_0;
function Key_getInstance() {
  if (Key_instance_0 == null)
    new Key();
  return Key_instance_0;
}
function CoroutineDispatcher() {
  Key_getInstance();
  AbstractCoroutineContextElement.call(this, Key_instance);
}
protoOf(CoroutineDispatcher).v1k = function (context) {
  return true;
};
protoOf(CoroutineDispatcher).n9 = function (continuation) {
  return new DispatchedContinuation(this, continuation);
};
protoOf(CoroutineDispatcher).o9 = function (continuation) {
  var dispatched = continuation instanceof DispatchedContinuation ? continuation : THROW_CCE();
  dispatched.x1k();
};
protoOf(CoroutineDispatcher).toString = function () {
  return get_classSimpleName(this) + '@' + get_hexAddress(this);
};
function handleCoroutineException(context, exception) {
  try {
    var tmp23_safe_receiver = context.m9(Key_instance_1);
    if (tmp23_safe_receiver == null)
      null;
    else {
      // Inline function 'kotlin.let' call
      tmp23_safe_receiver.y1k(context, exception);
      return Unit_instance;
    }
  } catch ($p) {
    if ($p instanceof Error) {
      var t = $p;
      handleUncaughtCoroutineException(context, handlerException(exception, t));
      return Unit_instance;
    } else {
      throw $p;
    }
  }
  handleUncaughtCoroutineException(context, exception);
}
function Key_0() {
}
var Key_instance_1;
function Key_getInstance_0() {
  return Key_instance_1;
}
function handlerException(originalException, thrownException) {
  if (originalException === thrownException)
    return originalException;
  // Inline function 'kotlin.apply' call
  var this_0 = RuntimeException_init_$Create$('Exception while trying to handle coroutine exception', thrownException);
  // Inline function 'kotlinx.coroutines.handlerException.<anonymous>' call
  addSuppressed(this_0, originalException);
  return this_0;
}
function CoroutineScope() {
}
function MainScope() {
  return new ContextScope(SupervisorJob().wg(Dispatchers_getInstance().d1l()));
}
function GlobalScope() {
}
protoOf(GlobalScope).s1f = function () {
  return EmptyCoroutineContext_getInstance();
};
var GlobalScope_instance;
function GlobalScope_getInstance() {
  return GlobalScope_instance;
}
var CoroutineStart_DEFAULT_instance;
var CoroutineStart_LAZY_instance;
var CoroutineStart_ATOMIC_instance;
var CoroutineStart_UNDISPATCHED_instance;
var CoroutineStart_entriesInitialized;
function CoroutineStart_initEntries() {
  if (CoroutineStart_entriesInitialized)
    return Unit_instance;
  CoroutineStart_entriesInitialized = true;
  CoroutineStart_DEFAULT_instance = new CoroutineStart('DEFAULT', 0);
  CoroutineStart_LAZY_instance = new CoroutineStart('LAZY', 1);
  CoroutineStart_ATOMIC_instance = new CoroutineStart('ATOMIC', 2);
  CoroutineStart_UNDISPATCHED_instance = new CoroutineStart('UNDISPATCHED', 3);
}
function CoroutineStart(name, ordinal) {
  Enum.call(this, name, ordinal);
}
protoOf(CoroutineStart).j1g = function (block, receiver, completion) {
  var tmp;
  switch (this.l2_1) {
    case 0:
      startCoroutineCancellable_0(block, receiver, completion);
      tmp = Unit_instance;
      break;
    case 2:
      startCoroutine(block, receiver, completion);
      tmp = Unit_instance;
      break;
    case 3:
      startCoroutineUndispatched(block, receiver, completion);
      tmp = Unit_instance;
      break;
    case 1:
      tmp = Unit_instance;
      break;
    default:
      noWhenBranchMatchedException();
      break;
  }
  return tmp;
};
protoOf(CoroutineStart).k1h = function () {
  return this === CoroutineStart_LAZY_getInstance();
};
function CoroutineStart_DEFAULT_getInstance() {
  CoroutineStart_initEntries();
  return CoroutineStart_DEFAULT_instance;
}
function CoroutineStart_LAZY_getInstance() {
  CoroutineStart_initEntries();
  return CoroutineStart_LAZY_instance;
}
function Delay() {
}
function get_delay(_this__u8e3s4) {
  var tmp = _this__u8e3s4.m9(Key_instance);
  var tmp0_elvis_lhs = (!(tmp == null) ? isInterface(tmp, Delay) : false) ? tmp : null;
  return tmp0_elvis_lhs == null ? get_DefaultDelay() : tmp0_elvis_lhs;
}
function delay(timeMillis, $completion) {
  if (timeMillis.d1(new Long(0, 0)) <= 0)
    return Unit_instance;
  // Inline function 'kotlinx.coroutines.suspendCancellableCoroutine.<anonymous>' call
  var cancellable = new CancellableContinuationImpl(intercepted($completion), 1);
  cancellable.c1j();
  // Inline function 'kotlinx.coroutines.delay.<anonymous>' call
  if (timeMillis.d1(new Long(-1, 2147483647)) < 0) {
    get_delay(cancellable.f9()).e1l(timeMillis, cancellable);
  }
  return cancellable.m1j();
}
function delta($this, unconfined) {
  return unconfined ? new Long(0, 1) : new Long(1, 0);
}
function EventLoop() {
  CoroutineDispatcher.call(this);
  this.g1l_1 = new Long(0, 0);
  this.h1l_1 = false;
  this.i1l_1 = null;
}
protoOf(EventLoop).j1l = function () {
  var tmp0_elvis_lhs = this.i1l_1;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return false;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var queue = tmp;
  var tmp1_elvis_lhs = queue.ue();
  var tmp_0;
  if (tmp1_elvis_lhs == null) {
    return false;
  } else {
    tmp_0 = tmp1_elvis_lhs;
  }
  var task = tmp_0;
  task.z1j();
  return true;
};
protoOf(EventLoop).k1l = function (task) {
  var tmp0_elvis_lhs = this.i1l_1;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    // Inline function 'kotlin.also' call
    var this_0 = ArrayDeque_init_$Create$();
    // Inline function 'kotlinx.coroutines.EventLoop.dispatchUnconfined.<anonymous>' call
    this.i1l_1 = this_0;
    tmp = this_0;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var queue = tmp;
  queue.se(task);
};
protoOf(EventLoop).l1l = function () {
  return this.g1l_1.d1(delta(this, true)) >= 0;
};
protoOf(EventLoop).m1l = function () {
  var tmp27_safe_receiver = this.i1l_1;
  var tmp0_elvis_lhs = tmp27_safe_receiver == null ? null : tmp27_safe_receiver.p();
  return tmp0_elvis_lhs == null ? true : tmp0_elvis_lhs;
};
protoOf(EventLoop).n1l = function (unconfined) {
  this.g1l_1 = this.g1l_1.r2(delta(this, unconfined));
  if (!unconfined)
    this.h1l_1 = true;
};
protoOf(EventLoop).o1l = function (unconfined) {
  this.g1l_1 = this.g1l_1.s2(delta(this, unconfined));
  if (this.g1l_1.d1(new Long(0, 0)) > 0)
    return Unit_instance;
  // Inline function 'kotlinx.coroutines.assert' call
  if (this.h1l_1) {
    this.p1l();
  }
};
protoOf(EventLoop).p1l = function () {
};
function ThreadLocalEventLoop() {
  ThreadLocalEventLoop_instance = this;
  this.q1l_1 = commonThreadLocal(new Symbol('ThreadLocalEventLoop'));
}
protoOf(ThreadLocalEventLoop).r1l = function () {
  var tmp0_elvis_lhs = this.q1l_1.t1l();
  var tmp;
  if (tmp0_elvis_lhs == null) {
    // Inline function 'kotlin.also' call
    var this_0 = createEventLoop();
    // Inline function 'kotlinx.coroutines.ThreadLocalEventLoop.<get-eventLoop>.<anonymous>' call
    ThreadLocalEventLoop_getInstance().q1l_1.u1l(this_0);
    tmp = this_0;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  return tmp;
};
var ThreadLocalEventLoop_instance;
function ThreadLocalEventLoop_getInstance() {
  if (ThreadLocalEventLoop_instance == null)
    new ThreadLocalEventLoop();
  return ThreadLocalEventLoop_instance;
}
function CompletionHandlerException(message, cause) {
  RuntimeException_init_$Init$(message, cause, this);
  captureStack(this, CompletionHandlerException);
}
function CoroutinesInternalError(message, cause) {
  Error_init_$Init$(message, cause, this);
  captureStack(this, CoroutinesInternalError);
}
function Key_1() {
}
var Key_instance_2;
function Key_getInstance_1() {
  return Key_instance_2;
}
function ParentJob() {
}
function NonDisposableHandle() {
}
protoOf(NonDisposableHandle).d1j = function () {
};
protoOf(NonDisposableHandle).z1g = function (cause) {
  return false;
};
protoOf(NonDisposableHandle).toString = function () {
  return 'NonDisposableHandle';
};
var NonDisposableHandle_instance;
function NonDisposableHandle_getInstance() {
  return NonDisposableHandle_instance;
}
function invokeOnCompletion(_this__u8e3s4, invokeImmediately, handler) {
  invokeImmediately = invokeImmediately === VOID ? true : invokeImmediately;
  var tmp;
  if (_this__u8e3s4 instanceof JobSupport) {
    tmp = _this__u8e3s4.v1g(invokeImmediately, handler);
  } else {
    var tmp_0 = handler.h1k();
    tmp = _this__u8e3s4.u1g(tmp_0, invokeImmediately, JobNode$invoke$ref(handler));
  }
  return tmp;
}
function JobNode$invoke$ref($boundThis) {
  var l = function (p0) {
    $boundThis.j1j(p0);
    return Unit_instance;
  };
  l.callableName = 'invoke';
  return l;
}
function get_COMPLETING_ALREADY() {
  _init_properties_JobSupport_kt__68f172();
  return COMPLETING_ALREADY;
}
var COMPLETING_ALREADY;
function get_COMPLETING_WAITING_CHILDREN() {
  _init_properties_JobSupport_kt__68f172();
  return COMPLETING_WAITING_CHILDREN;
}
var COMPLETING_WAITING_CHILDREN;
function get_COMPLETING_RETRY() {
  _init_properties_JobSupport_kt__68f172();
  return COMPLETING_RETRY;
}
var COMPLETING_RETRY;
function get_TOO_LATE_TO_CANCEL() {
  _init_properties_JobSupport_kt__68f172();
  return TOO_LATE_TO_CANCEL;
}
var TOO_LATE_TO_CANCEL;
function get_SEALED() {
  _init_properties_JobSupport_kt__68f172();
  return SEALED;
}
var SEALED;
function get_EMPTY_NEW() {
  _init_properties_JobSupport_kt__68f172();
  return EMPTY_NEW;
}
var EMPTY_NEW;
function get_EMPTY_ACTIVE() {
  _init_properties_JobSupport_kt__68f172();
  return EMPTY_ACTIVE;
}
var EMPTY_ACTIVE;
function Empty(isActive) {
  this.v1l_1 = isActive;
}
protoOf(Empty).t1f = function () {
  return this.v1l_1;
};
protoOf(Empty).n1k = function () {
  return null;
};
protoOf(Empty).toString = function () {
  return 'Empty{' + (this.v1l_1 ? 'Active' : 'New') + '}';
};
function Incomplete() {
}
function NodeList() {
  LockFreeLinkedListHead.call(this);
}
protoOf(NodeList).t1f = function () {
  return true;
};
protoOf(NodeList).n1k = function () {
  return this;
};
protoOf(NodeList).z1l = function (state) {
  // Inline function 'kotlin.text.buildString' call
  // Inline function 'kotlin.apply' call
  var this_0 = StringBuilder_init_$Create$();
  // Inline function 'kotlinx.coroutines.NodeList.getString.<anonymous>' call
  this_0.l8('List{');
  this_0.l8(state);
  this_0.l8('}[');
  var first = true;
  // Inline function 'kotlinx.coroutines.internal.LockFreeLinkedListHead.forEach' call
  var cur = this.o1k_1;
  while (!equals(cur, this)) {
    // Inline function 'kotlinx.coroutines.NodeList.getString.<anonymous>.<anonymous>' call
    var node = cur;
    if (node instanceof JobNode) {
      if (first) {
        first = false;
      } else
        this_0.l8(', ');
      this_0.k8(node);
    }
    cur = cur.o1k_1;
  }
  this_0.l8(']');
  return this_0.toString();
};
protoOf(NodeList).toString = function () {
  return get_DEBUG() ? this.z1l('Active') : protoOf(LockFreeLinkedListHead).toString.call(this);
};
function JobNode() {
  LockFreeLinkedListNode.call(this);
}
protoOf(JobNode).m1k = function () {
  var tmp = this.l1k_1;
  if (!(tmp == null))
    return tmp;
  else {
    throwUninitializedPropertyAccessException('job');
  }
};
protoOf(JobNode).t1f = function () {
  return true;
};
protoOf(JobNode).n1k = function () {
  return null;
};
protoOf(JobNode).d1j = function () {
  return this.m1k().w1g(this);
};
protoOf(JobNode).toString = function () {
  return get_classSimpleName(this) + '@' + get_hexAddress(this) + '[job@' + get_hexAddress(this.m1k()) + ']';
};
function _set_exceptionsHolder__tqm22h($this, value) {
  $this.e1m_1.kotlinx$atomicfu$value = value;
}
function _get_exceptionsHolder__nhszp($this) {
  return $this.e1m_1.kotlinx$atomicfu$value;
}
function allocateList($this) {
  return ArrayList_init_$Create$(4);
}
function finalizeFinishingState($this, state, proposedUpdate) {
  // Inline function 'kotlinx.coroutines.assert' call
  // Inline function 'kotlinx.coroutines.assert' call
  // Inline function 'kotlinx.coroutines.assert' call
  var tmp46_safe_receiver = proposedUpdate instanceof CompletedExceptionally ? proposedUpdate : null;
  var proposedException = tmp46_safe_receiver == null ? null : tmp46_safe_receiver.y1f_1;
  var wasCancelling;
  // Inline function 'kotlinx.coroutines.internal.synchronized' call
  // Inline function 'kotlinx.coroutines.internal.synchronizedImpl' call
  // Inline function 'kotlinx.coroutines.JobSupport.finalizeFinishingState.<anonymous>' call
  wasCancelling = state.f1m();
  var exceptions = state.g1m(proposedException);
  var finalCause = getFinalRootCause($this, state, exceptions);
  if (!(finalCause == null)) {
    addSuppressedExceptions($this, finalCause, exceptions);
  }
  var finalException = finalCause;
  var finalState = finalException == null ? proposedUpdate : finalException === proposedException ? proposedUpdate : new CompletedExceptionally(finalException);
  if (!(finalException == null)) {
    var handled = cancelParent($this, finalException) || $this.g1h(finalException);
    if (handled) {
      (finalState instanceof CompletedExceptionally ? finalState : THROW_CCE()).z1i();
    }
  }
  if (!wasCancelling) {
    $this.d1h(finalException);
  }
  $this.x1f(finalState);
  var casSuccess = $this.m1f_1.atomicfu$compareAndSet(state, boxIncomplete(finalState));
  // Inline function 'kotlinx.coroutines.assert' call
  completeStateFinalization($this, state, finalState);
  return finalState;
}
function getFinalRootCause($this, state, exceptions) {
  if (exceptions.p()) {
    if (state.f1m()) {
      // Inline function 'kotlinx.coroutines.JobSupport.defaultCancellationException' call
      return new JobCancellationException(null == null ? $this.w1f() : null, null, $this);
    }
    return null;
  }
  var tmp$ret$2;
  $l$block: {
    // Inline function 'kotlin.collections.firstOrNull' call
    var _iterator__ex2g4s = exceptions.j();
    while (_iterator__ex2g4s.k()) {
      var element = _iterator__ex2g4s.l();
      // Inline function 'kotlinx.coroutines.JobSupport.getFinalRootCause.<anonymous>' call
      if (!(element instanceof CancellationException)) {
        tmp$ret$2 = element;
        break $l$block;
      }
    }
    tmp$ret$2 = null;
  }
  var firstNonCancellation = tmp$ret$2;
  if (!(firstNonCancellation == null))
    return firstNonCancellation;
  var first = exceptions.o(0);
  if (first instanceof TimeoutCancellationException) {
    var tmp$ret$4;
    $l$block_0: {
      // Inline function 'kotlin.collections.firstOrNull' call
      var _iterator__ex2g4s_0 = exceptions.j();
      while (_iterator__ex2g4s_0.k()) {
        var element_0 = _iterator__ex2g4s_0.l();
        // Inline function 'kotlinx.coroutines.JobSupport.getFinalRootCause.<anonymous>' call
        var tmp;
        if (!(element_0 === first)) {
          tmp = element_0 instanceof TimeoutCancellationException;
        } else {
          tmp = false;
        }
        if (tmp) {
          tmp$ret$4 = element_0;
          break $l$block_0;
        }
      }
      tmp$ret$4 = null;
    }
    var detailedTimeoutException = tmp$ret$4;
    if (!(detailedTimeoutException == null))
      return detailedTimeoutException;
  }
  return first;
}
function addSuppressedExceptions($this, rootCause, exceptions) {
  if (exceptions.m() <= 1)
    return Unit_instance;
  var seenExceptions = identitySet(exceptions.m());
  var unwrappedCause = unwrap(rootCause);
  var _iterator__ex2g4s = exceptions.j();
  while (_iterator__ex2g4s.k()) {
    var exception = _iterator__ex2g4s.l();
    var unwrapped = unwrap(exception);
    var tmp;
    var tmp_0;
    if (!(unwrapped === rootCause) && !(unwrapped === unwrappedCause)) {
      tmp_0 = !(unwrapped instanceof CancellationException);
    } else {
      tmp_0 = false;
    }
    if (tmp_0) {
      tmp = seenExceptions.e(unwrapped);
    } else {
      tmp = false;
    }
    if (tmp) {
      addSuppressed(rootCause, unwrapped);
    }
  }
}
function tryFinalizeSimpleState($this, state, update) {
  // Inline function 'kotlinx.coroutines.assert' call
  // Inline function 'kotlinx.coroutines.assert' call
  if (!$this.m1f_1.atomicfu$compareAndSet(state, boxIncomplete(update)))
    return false;
  $this.d1h(null);
  $this.x1f(update);
  completeStateFinalization($this, state, update);
  return true;
}
function completeStateFinalization($this, state, update) {
  var tmp47_safe_receiver = $this.l1g();
  if (tmp47_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlinx.coroutines.JobSupport.completeStateFinalization.<anonymous>' call
    tmp47_safe_receiver.d1j();
    $this.k1g(NonDisposableHandle_instance);
  }
  var tmp48_safe_receiver = update instanceof CompletedExceptionally ? update : null;
  var cause = tmp48_safe_receiver == null ? null : tmp48_safe_receiver.y1f_1;
  if (state instanceof JobNode) {
    try {
      state.j1j(cause);
    } catch ($p) {
      if ($p instanceof Error) {
        var ex = $p;
        $this.e1g(new CompletionHandlerException('Exception in completion handler ' + state.toString() + ' for ' + $this.toString(), ex));
      } else {
        throw $p;
      }
    }
  } else {
    var tmp49_safe_receiver = state.n1k();
    if (tmp49_safe_receiver == null)
      null;
    else {
      notifyCompletion(tmp49_safe_receiver, $this, cause);
    }
  }
}
function notifyCancelling($this, list, cause) {
  $this.d1h(cause);
  list.a1m(4);
  // Inline function 'kotlinx.coroutines.JobSupport.notifyHandlers' call
  var exception = null;
  // Inline function 'kotlinx.coroutines.internal.LockFreeLinkedListHead.forEach' call
  var cur = list.o1k_1;
  while (!equals(cur, list)) {
    // Inline function 'kotlinx.coroutines.JobSupport.notifyHandlers.<anonymous>' call
    var node = cur;
    var tmp;
    if (node instanceof JobNode) {
      // Inline function 'kotlinx.coroutines.JobSupport.notifyCancelling.<anonymous>' call
      tmp = node.h1k();
    } else {
      tmp = false;
    }
    if (tmp) {
      try {
        node.j1j(cause);
      } catch ($p) {
        if ($p instanceof Error) {
          var ex = $p;
          var tmp50_safe_receiver = exception;
          var tmp_0;
          if (tmp50_safe_receiver == null) {
            tmp_0 = null;
          } else {
            // Inline function 'kotlin.apply' call
            // Inline function 'kotlinx.coroutines.JobSupport.notifyHandlers.<anonymous>.<anonymous>' call
            addSuppressed(tmp50_safe_receiver, ex);
            tmp_0 = tmp50_safe_receiver;
          }
          if (tmp_0 == null) {
            // Inline function 'kotlin.run' call
            // Inline function 'kotlinx.coroutines.JobSupport.notifyHandlers.<anonymous>.<anonymous>' call
            exception = new CompletionHandlerException('Exception in completion handler ' + node.toString() + ' for ' + $this.toString(), ex);
          }
        } else {
          throw $p;
        }
      }
    }
    cur = cur.o1k_1;
  }
  var tmp51_safe_receiver = exception;
  if (tmp51_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlinx.coroutines.JobSupport.notifyHandlers.<anonymous>' call
    $this.e1g(tmp51_safe_receiver);
  }
  cancelParent($this, cause);
}
function cancelParent($this, cause) {
  if ($this.e1h())
    return true;
  var isCancellation = cause instanceof CancellationException;
  var parent = $this.l1g();
  if (parent === null || parent === NonDisposableHandle_instance) {
    return isCancellation;
  }
  return parent.z1g(cause) || isCancellation;
}
function notifyCompletion(_this__u8e3s4, $this, cause) {
  _this__u8e3s4.a1m(1);
  // Inline function 'kotlinx.coroutines.JobSupport.notifyHandlers' call
  var exception = null;
  // Inline function 'kotlinx.coroutines.internal.LockFreeLinkedListHead.forEach' call
  var cur = _this__u8e3s4.o1k_1;
  while (!equals(cur, _this__u8e3s4)) {
    // Inline function 'kotlinx.coroutines.JobSupport.notifyHandlers.<anonymous>' call
    var node = cur;
    var tmp;
    if (node instanceof JobNode) {
      // Inline function 'kotlinx.coroutines.JobSupport.notifyCompletion.<anonymous>' call
      tmp = true;
    } else {
      tmp = false;
    }
    if (tmp) {
      try {
        node.j1j(cause);
      } catch ($p) {
        if ($p instanceof Error) {
          var ex = $p;
          var tmp50_safe_receiver = exception;
          var tmp_0;
          if (tmp50_safe_receiver == null) {
            tmp_0 = null;
          } else {
            // Inline function 'kotlin.apply' call
            // Inline function 'kotlinx.coroutines.JobSupport.notifyHandlers.<anonymous>.<anonymous>' call
            addSuppressed(tmp50_safe_receiver, ex);
            tmp_0 = tmp50_safe_receiver;
          }
          if (tmp_0 == null) {
            // Inline function 'kotlin.run' call
            // Inline function 'kotlinx.coroutines.JobSupport.notifyHandlers.<anonymous>.<anonymous>' call
            exception = new CompletionHandlerException('Exception in completion handler ' + node.toString() + ' for ' + $this.toString(), ex);
          }
        } else {
          throw $p;
        }
      }
    }
    cur = cur.o1k_1;
  }
  var tmp51_safe_receiver = exception;
  if (tmp51_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlinx.coroutines.JobSupport.notifyHandlers.<anonymous>' call
    $this.e1g(tmp51_safe_receiver);
  }
}
function startInternal($this, state) {
  if (state instanceof Empty) {
    if (state.v1l_1)
      return 0;
    if (!$this.m1f_1.atomicfu$compareAndSet(state, get_EMPTY_ACTIVE()))
      return -1;
    $this.p1g();
    return 1;
  } else {
    if (state instanceof InactiveNodeList) {
      if (!$this.m1f_1.atomicfu$compareAndSet(state, state.h1m_1))
        return -1;
      $this.p1g();
      return 1;
    } else {
      return 0;
    }
  }
}
function promoteEmptyToNodeList($this, state) {
  var list = new NodeList();
  var update = state.v1l_1 ? list : new InactiveNodeList(list);
  $this.m1f_1.atomicfu$compareAndSet(state, update);
}
function promoteSingleToNodeList($this, state) {
  state.t1k(new NodeList());
  // Inline function 'kotlinx.coroutines.internal.LockFreeLinkedListNode.nextNode' call
  var list = state.o1k_1;
  $this.m1f_1.atomicfu$compareAndSet(state, list);
}
function cancelMakeCompleting($this, cause) {
  // Inline function 'kotlinx.coroutines.JobSupport.loopOnState' call
  while (true) {
    // Inline function 'kotlinx.coroutines.JobSupport.cancelMakeCompleting.<anonymous>' call
    var state = $this.m1g();
    var tmp;
    if (!(!(state == null) ? isInterface(state, Incomplete) : false)) {
      tmp = true;
    } else {
      var tmp_0;
      if (state instanceof Finishing) {
        tmp_0 = state.i1m();
      } else {
        tmp_0 = false;
      }
      tmp = tmp_0;
    }
    if (tmp) {
      return get_COMPLETING_ALREADY();
    }
    var proposedUpdate = new CompletedExceptionally(createCauseException($this, cause));
    var finalState = tryMakeCompleting($this, state, proposedUpdate);
    if (!(finalState === get_COMPLETING_RETRY()))
      return finalState;
  }
}
function createCauseException($this, cause) {
  var tmp;
  if (cause == null ? true : cause instanceof Error) {
    var tmp_0;
    if (cause == null) {
      // Inline function 'kotlinx.coroutines.JobSupport.defaultCancellationException' call
      tmp_0 = new JobCancellationException(null == null ? $this.w1f() : null, null, $this);
    } else {
      tmp_0 = cause;
    }
    tmp = tmp_0;
  } else {
    tmp = ((!(cause == null) ? isInterface(cause, ParentJob) : false) ? cause : THROW_CCE()).b1h();
  }
  return tmp;
}
function makeCancelling($this, cause) {
  var causeExceptionCache = null;
  // Inline function 'kotlinx.coroutines.JobSupport.loopOnState' call
  while (true) {
    var tmp1 = $this.m1g();
    $l$block: {
      // Inline function 'kotlinx.coroutines.JobSupport.makeCancelling.<anonymous>' call
      if (tmp1 instanceof Finishing) {
        // Inline function 'kotlinx.coroutines.internal.synchronized' call
        // Inline function 'kotlinx.coroutines.internal.synchronizedImpl' call
        // Inline function 'kotlinx.coroutines.JobSupport.makeCancelling.<anonymous>.<anonymous>' call
        if (tmp1.j1m())
          return get_TOO_LATE_TO_CANCEL();
        var wasCancelling = tmp1.f1m();
        if (!(cause == null) || !wasCancelling) {
          var tmp0_elvis_lhs = causeExceptionCache;
          var tmp;
          if (tmp0_elvis_lhs == null) {
            // Inline function 'kotlin.also' call
            var this_0 = createCauseException($this, cause);
            // Inline function 'kotlinx.coroutines.JobSupport.makeCancelling.<anonymous>.<anonymous>.<anonymous>' call
            causeExceptionCache = this_0;
            tmp = this_0;
          } else {
            tmp = tmp0_elvis_lhs;
          }
          var causeException = tmp;
          tmp1.k1m(causeException);
        }
        // Inline function 'kotlin.takeIf' call
        var this_1 = tmp1.l1m();
        var tmp_0;
        // Inline function 'kotlinx.coroutines.JobSupport.makeCancelling.<anonymous>.<anonymous>.<anonymous>' call
        if (!wasCancelling) {
          tmp_0 = this_1;
        } else {
          tmp_0 = null;
        }
        var notifyRootCause = tmp_0;
        if (notifyRootCause == null)
          null;
        else {
          // Inline function 'kotlin.let' call
          // Inline function 'kotlinx.coroutines.JobSupport.makeCancelling.<anonymous>.<anonymous>' call
          notifyCancelling($this, tmp1.b1m_1, notifyRootCause);
        }
        return get_COMPLETING_ALREADY();
      } else {
        if (!(tmp1 == null) ? isInterface(tmp1, Incomplete) : false) {
          var tmp0_elvis_lhs_0 = causeExceptionCache;
          var tmp_1;
          if (tmp0_elvis_lhs_0 == null) {
            // Inline function 'kotlin.also' call
            var this_2 = createCauseException($this, cause);
            // Inline function 'kotlinx.coroutines.JobSupport.makeCancelling.<anonymous>.<anonymous>' call
            causeExceptionCache = this_2;
            tmp_1 = this_2;
          } else {
            tmp_1 = tmp0_elvis_lhs_0;
          }
          var causeException_0 = tmp_1;
          if (tmp1.t1f()) {
            if (tryMakeCancelling($this, tmp1, causeException_0))
              return get_COMPLETING_ALREADY();
          } else {
            var finalState = tryMakeCompleting($this, tmp1, new CompletedExceptionally(causeException_0));
            if (finalState === get_COMPLETING_ALREADY()) {
              // Inline function 'kotlin.error' call
              var message = 'Cannot happen in ' + toString(tmp1);
              throw IllegalStateException_init_$Create$(toString(message));
            } else if (finalState === get_COMPLETING_RETRY()) {
              break $l$block;
            } else
              return finalState;
          }
        } else {
          return get_TOO_LATE_TO_CANCEL();
        }
      }
    }
  }
}
function getOrPromoteCancellingList($this, state) {
  var tmp0_elvis_lhs = state.n1k();
  var tmp;
  if (tmp0_elvis_lhs == null) {
    var tmp_0;
    if (state instanceof Empty) {
      tmp_0 = new NodeList();
    } else {
      if (state instanceof JobNode) {
        promoteSingleToNodeList($this, state);
        tmp_0 = null;
      } else {
        var message = 'State should have list: ' + toString(state);
        throw IllegalStateException_init_$Create$(toString(message));
      }
    }
    tmp = tmp_0;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  return tmp;
}
function tryMakeCancelling($this, state, rootCause) {
  // Inline function 'kotlinx.coroutines.assert' call
  // Inline function 'kotlinx.coroutines.assert' call
  var tmp0_elvis_lhs = getOrPromoteCancellingList($this, state);
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return false;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var list = tmp;
  var cancelling = new Finishing(list, false, rootCause);
  if (!$this.m1f_1.atomicfu$compareAndSet(state, cancelling))
    return false;
  notifyCancelling($this, list, rootCause);
  return true;
}
function tryMakeCompleting($this, state, proposedUpdate) {
  if (!(!(state == null) ? isInterface(state, Incomplete) : false))
    return get_COMPLETING_ALREADY();
  var tmp;
  var tmp_0;
  var tmp_1;
  if (state instanceof Empty) {
    tmp_1 = true;
  } else {
    tmp_1 = state instanceof JobNode;
  }
  if (tmp_1) {
    tmp_0 = !(state instanceof ChildHandleNode);
  } else {
    tmp_0 = false;
  }
  if (tmp_0) {
    tmp = !(proposedUpdate instanceof CompletedExceptionally);
  } else {
    tmp = false;
  }
  if (tmp) {
    if (tryFinalizeSimpleState($this, state, proposedUpdate)) {
      return proposedUpdate;
    }
    return get_COMPLETING_RETRY();
  }
  return tryMakeCompletingSlowPath($this, state, proposedUpdate);
}
function tryMakeCompletingSlowPath($this, state, proposedUpdate) {
  var tmp0_elvis_lhs = getOrPromoteCancellingList($this, state);
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return get_COMPLETING_RETRY();
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var list = tmp;
  var tmp1_elvis_lhs = state instanceof Finishing ? state : null;
  var finishing = tmp1_elvis_lhs == null ? new Finishing(list, false, null) : tmp1_elvis_lhs;
  var notifyRootCause;
  // Inline function 'kotlinx.coroutines.internal.synchronized' call
  // Inline function 'kotlinx.coroutines.internal.synchronizedImpl' call
  if (finishing.i1m())
    return get_COMPLETING_ALREADY();
  finishing.m1m(true);
  if (!(finishing === state)) {
    if (!$this.m1f_1.atomicfu$compareAndSet(state, finishing))
      return get_COMPLETING_RETRY();
  }
  // Inline function 'kotlinx.coroutines.assert' call
  var wasCancelling = finishing.f1m();
  var tmp65_safe_receiver = proposedUpdate instanceof CompletedExceptionally ? proposedUpdate : null;
  if (tmp65_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlinx.coroutines.JobSupport.tryMakeCompletingSlowPath.<anonymous>.<anonymous>' call
    finishing.k1m(tmp65_safe_receiver.y1f_1);
  }
  // Inline function 'kotlin.takeIf' call
  var this_0 = finishing.l1m();
  var tmp_0;
  // Inline function 'kotlinx.coroutines.JobSupport.tryMakeCompletingSlowPath.<anonymous>.<anonymous>' call
  if (!wasCancelling) {
    tmp_0 = this_0;
  } else {
    tmp_0 = null;
  }
  notifyRootCause = tmp_0;
  if (notifyRootCause == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlinx.coroutines.JobSupport.tryMakeCompletingSlowPath.<anonymous>' call
    notifyCancelling($this, list, notifyRootCause);
  }
  var child = nextChild(list, $this);
  if (!(child == null) && tryWaitForChild($this, finishing, child, proposedUpdate))
    return get_COMPLETING_WAITING_CHILDREN();
  list.a1m(2);
  var anotherChild = nextChild(list, $this);
  if (!(anotherChild == null) && tryWaitForChild($this, finishing, anotherChild, proposedUpdate))
    return get_COMPLETING_WAITING_CHILDREN();
  return finalizeFinishingState($this, finishing, proposedUpdate);
}
function _get_exceptionOrNull__b3j7js(_this__u8e3s4, $this) {
  var tmp67_safe_receiver = _this__u8e3s4 instanceof CompletedExceptionally ? _this__u8e3s4 : null;
  return tmp67_safe_receiver == null ? null : tmp67_safe_receiver.y1f_1;
}
function tryWaitForChild($this, state, child, proposedUpdate) {
  var $this_0 = $this;
  var state_0 = state;
  var child_0 = child;
  var proposedUpdate_0 = proposedUpdate;
  $l$1: do {
    $l$0: do {
      var handle = invokeOnCompletion(child_0.r1m_1, false, new ChildCompletion($this_0, state_0, child_0, proposedUpdate_0));
      if (!(handle === NonDisposableHandle_instance))
        return true;
      var tmp0_elvis_lhs = nextChild(child_0, $this_0);
      var tmp;
      if (tmp0_elvis_lhs == null) {
        return false;
      } else {
        tmp = tmp0_elvis_lhs;
      }
      var nextChild_0 = tmp;
      var tmp0 = $this_0;
      var tmp1 = state_0;
      var tmp3 = proposedUpdate_0;
      $this_0 = tmp0;
      state_0 = tmp1;
      child_0 = nextChild_0;
      proposedUpdate_0 = tmp3;
      continue $l$0;
    }
     while (false);
  }
   while (true);
}
function continueCompleting($this, state, lastChild, proposedUpdate) {
  // Inline function 'kotlinx.coroutines.assert' call
  var waitChild = nextChild(lastChild, $this);
  if (!(waitChild == null) && tryWaitForChild($this, state, waitChild, proposedUpdate))
    return Unit_instance;
  state.b1m_1.a1m(2);
  var waitChildAgain = nextChild(lastChild, $this);
  if (!(waitChildAgain == null) && tryWaitForChild($this, state, waitChildAgain, proposedUpdate)) {
    return Unit_instance;
  }
  var finalState = finalizeFinishingState($this, state, proposedUpdate);
  $this.d1g(finalState);
}
function nextChild(_this__u8e3s4, $this) {
  var cur = _this__u8e3s4;
  $l$loop: while (true) {
    // Inline function 'kotlinx.coroutines.internal.LockFreeLinkedListNode.isRemoved' call
    if (!cur.q1k_1) {
      break $l$loop;
    }
    // Inline function 'kotlinx.coroutines.internal.LockFreeLinkedListNode.prevNode' call
    cur = cur.p1k_1;
  }
  $l$loop_0: while (true) {
    // Inline function 'kotlinx.coroutines.internal.LockFreeLinkedListNode.nextNode' call
    cur = cur.o1k_1;
    // Inline function 'kotlinx.coroutines.internal.LockFreeLinkedListNode.isRemoved' call
    if (cur.q1k_1)
      continue $l$loop_0;
    if (cur instanceof ChildHandleNode)
      return cur;
    if (cur instanceof NodeList)
      return null;
  }
}
function stateString($this, state) {
  var tmp;
  if (state instanceof Finishing) {
    tmp = state.f1m() ? 'Cancelling' : state.i1m() ? 'Completing' : 'Active';
  } else {
    if (!(state == null) ? isInterface(state, Incomplete) : false) {
      tmp = state.t1f() ? 'Active' : 'New';
    } else {
      if (state instanceof CompletedExceptionally) {
        tmp = 'Cancelled';
      } else {
        tmp = 'Completed';
      }
    }
  }
  return tmp;
}
function Finishing(list, isCompleting, rootCause) {
  SynchronizedObject.call(this);
  this.b1m_1 = list;
  this.c1m_1 = atomic$boolean$1(isCompleting);
  this.d1m_1 = atomic$ref$1(rootCause);
  this.e1m_1 = atomic$ref$1(null);
}
protoOf(Finishing).n1k = function () {
  return this.b1m_1;
};
protoOf(Finishing).m1m = function (value) {
  this.c1m_1.kotlinx$atomicfu$value = value;
};
protoOf(Finishing).i1m = function () {
  return this.c1m_1.kotlinx$atomicfu$value;
};
protoOf(Finishing).s1m = function (value) {
  this.d1m_1.kotlinx$atomicfu$value = value;
};
protoOf(Finishing).l1m = function () {
  return this.d1m_1.kotlinx$atomicfu$value;
};
protoOf(Finishing).j1m = function () {
  return _get_exceptionsHolder__nhszp(this) === get_SEALED();
};
protoOf(Finishing).f1m = function () {
  return !(this.l1m() == null);
};
protoOf(Finishing).t1f = function () {
  return this.l1m() == null;
};
protoOf(Finishing).g1m = function (proposedException) {
  var eh = _get_exceptionsHolder__nhszp(this);
  var tmp;
  if (eh == null) {
    tmp = allocateList(this);
  } else {
    if (eh instanceof Error) {
      // Inline function 'kotlin.also' call
      var this_0 = allocateList(this);
      // Inline function 'kotlinx.coroutines.Finishing.sealLocked.<anonymous>' call
      this_0.e(eh);
      tmp = this_0;
    } else {
      if (eh instanceof ArrayList) {
        tmp = eh instanceof ArrayList ? eh : THROW_CCE();
      } else {
        var message = 'State is ' + toString_0(eh);
        throw IllegalStateException_init_$Create$(toString(message));
      }
    }
  }
  var list = tmp;
  var rootCause = this.l1m();
  if (rootCause == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlinx.coroutines.Finishing.sealLocked.<anonymous>' call
    list.x4(0, rootCause);
  }
  if (!(proposedException == null) && !equals(proposedException, rootCause)) {
    list.e(proposedException);
  }
  _set_exceptionsHolder__tqm22h(this, get_SEALED());
  return list;
};
protoOf(Finishing).k1m = function (exception) {
  var rootCause = this.l1m();
  if (rootCause == null) {
    this.s1m(exception);
    return Unit_instance;
  }
  if (exception === rootCause)
    return Unit_instance;
  var eh = _get_exceptionsHolder__nhszp(this);
  if (eh == null) {
    _set_exceptionsHolder__tqm22h(this, exception);
  } else {
    if (eh instanceof Error) {
      if (exception === eh)
        return Unit_instance;
      // Inline function 'kotlin.apply' call
      var this_0 = allocateList(this);
      // Inline function 'kotlinx.coroutines.Finishing.addExceptionLocked.<anonymous>' call
      this_0.e(eh);
      this_0.e(exception);
      _set_exceptionsHolder__tqm22h(this, this_0);
    } else {
      if (eh instanceof ArrayList) {
        (eh instanceof ArrayList ? eh : THROW_CCE()).e(exception);
      } else {
        // Inline function 'kotlin.error' call
        var message = 'State is ' + toString_0(eh);
        throw IllegalStateException_init_$Create$(toString(message));
      }
    }
  }
};
protoOf(Finishing).toString = function () {
  return 'Finishing[cancelling=' + this.f1m() + ', completing=' + this.i1m() + ', rootCause=' + toString_0(this.l1m()) + ', exceptions=' + toString_0(_get_exceptionsHolder__nhszp(this)) + ', list=' + this.b1m_1.toString() + ']';
};
function ChildCompletion(parent, state, child, proposedUpdate) {
  JobNode.call(this);
  this.x1m_1 = parent;
  this.y1m_1 = state;
  this.z1m_1 = child;
  this.a1n_1 = proposedUpdate;
}
protoOf(ChildCompletion).h1k = function () {
  return false;
};
protoOf(ChildCompletion).j1j = function (cause) {
  continueCompleting(this.x1m_1, this.y1m_1, this.z1m_1, this.a1n_1);
};
function JobSupport(active) {
  this.m1f_1 = atomic$ref$1(active ? get_EMPTY_ACTIVE() : get_EMPTY_NEW());
  this.n1f_1 = atomic$ref$1(null);
}
protoOf(JobSupport).w1 = function () {
  return Key_instance_2;
};
protoOf(JobSupport).k1g = function (value) {
  this.n1f_1.kotlinx$atomicfu$value = value;
};
protoOf(JobSupport).l1g = function () {
  return this.n1f_1.kotlinx$atomicfu$value;
};
protoOf(JobSupport).o1f = function (parent) {
  // Inline function 'kotlinx.coroutines.assert' call
  if (parent == null) {
    this.k1g(NonDisposableHandle_instance);
    return Unit_instance;
  }
  parent.o1g();
  var handle = parent.c1h(this);
  this.k1g(handle);
  if (this.n1g()) {
    handle.d1j();
    this.k1g(NonDisposableHandle_instance);
  }
};
protoOf(JobSupport).m1g = function () {
  return this.m1f_1.kotlinx$atomicfu$value;
};
protoOf(JobSupport).t1f = function () {
  var state = this.m1g();
  var tmp;
  if (!(state == null) ? isInterface(state, Incomplete) : false) {
    tmp = state.t1f();
  } else {
    tmp = false;
  }
  return tmp;
};
protoOf(JobSupport).n1g = function () {
  var tmp = this.m1g();
  return !(!(tmp == null) ? isInterface(tmp, Incomplete) : false);
};
protoOf(JobSupport).o1g = function () {
  // Inline function 'kotlinx.coroutines.JobSupport.loopOnState' call
  while (true) {
    // Inline function 'kotlinx.coroutines.JobSupport.start.<anonymous>' call
    var state = this.m1g();
    var tmp52_subject = startInternal(this, state);
    if (tmp52_subject === 0)
      return false;
    else if (tmp52_subject === 1)
      return true;
  }
};
protoOf(JobSupport).p1g = function () {
};
protoOf(JobSupport).q1g = function () {
  var state = this.m1g();
  var tmp;
  if (state instanceof Finishing) {
    var tmp54_safe_receiver = state.l1m();
    var tmp0_elvis_lhs = tmp54_safe_receiver == null ? null : this.r1g(tmp54_safe_receiver, get_classSimpleName(this) + ' is cancelling');
    var tmp_0;
    if (tmp0_elvis_lhs == null) {
      var message = 'Job is still new or active: ' + this.toString();
      throw IllegalStateException_init_$Create$(toString(message));
    } else {
      tmp_0 = tmp0_elvis_lhs;
    }
    tmp = tmp_0;
  } else {
    if (!(state == null) ? isInterface(state, Incomplete) : false) {
      var message_0 = 'Job is still new or active: ' + this.toString();
      throw IllegalStateException_init_$Create$(toString(message_0));
    } else {
      if (state instanceof CompletedExceptionally) {
        tmp = this.s1g(state.y1f_1);
      } else {
        tmp = new JobCancellationException(get_classSimpleName(this) + ' has completed normally', null, this);
      }
    }
  }
  return tmp;
};
protoOf(JobSupport).r1g = function (_this__u8e3s4, message) {
  var tmp0_elvis_lhs = _this__u8e3s4 instanceof CancellationException ? _this__u8e3s4 : null;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    // Inline function 'kotlinx.coroutines.JobSupport.defaultCancellationException' call
    tmp = new JobCancellationException(message == null ? this.w1f() : message, _this__u8e3s4, this);
  } else {
    tmp = tmp0_elvis_lhs;
  }
  return tmp;
};
protoOf(JobSupport).s1g = function (_this__u8e3s4, message, $super) {
  message = message === VOID ? null : message;
  return $super === VOID ? this.r1g(_this__u8e3s4, message) : $super.r1g.call(this, _this__u8e3s4, message);
};
protoOf(JobSupport).t1g = function (handler) {
  return this.v1g(true, new InvokeOnCompletion(handler));
};
protoOf(JobSupport).u1g = function (onCancelling, invokeImmediately, handler) {
  var tmp;
  if (onCancelling) {
    tmp = new InvokeOnCancelling(handler);
  } else {
    tmp = new InvokeOnCompletion(handler);
  }
  return this.v1g(invokeImmediately, tmp);
};
protoOf(JobSupport).v1g = function (invokeImmediately, node) {
  node.l1k_1 = this;
  var tmp$ret$0;
  $l$block_1: {
    // Inline function 'kotlinx.coroutines.JobSupport.tryPutNodeIntoList' call
    // Inline function 'kotlinx.coroutines.JobSupport.loopOnState' call
    while (true) {
      // Inline function 'kotlinx.coroutines.JobSupport.tryPutNodeIntoList.<anonymous>' call
      var state = this.m1g();
      if (state instanceof Empty) {
        if (state.v1l_1) {
          if (this.m1f_1.atomicfu$compareAndSet(state, node)) {
            tmp$ret$0 = true;
            break $l$block_1;
          }
        } else {
          promoteEmptyToNodeList(this, state);
        }
      } else {
        if (!(state == null) ? isInterface(state, Incomplete) : false) {
          var list = state.n1k();
          if (list == null) {
            promoteSingleToNodeList(this, state instanceof JobNode ? state : THROW_CCE());
          } else {
            // Inline function 'kotlinx.coroutines.JobSupport.invokeOnCompletionInternal.<anonymous>' call
            var tmp;
            if (node.h1k()) {
              var tmp55_safe_receiver = state instanceof Finishing ? state : null;
              var rootCause = tmp55_safe_receiver == null ? null : tmp55_safe_receiver.l1m();
              var tmp_0;
              if (rootCause == null) {
                tmp_0 = list.r1k(node, 5);
              } else {
                if (invokeImmediately) {
                  node.j1j(rootCause);
                }
                return NonDisposableHandle_instance;
              }
              tmp = tmp_0;
            } else {
              tmp = list.r1k(node, 1);
            }
            if (tmp) {
              tmp$ret$0 = true;
              break $l$block_1;
            }
          }
        } else {
          tmp$ret$0 = false;
          break $l$block_1;
        }
      }
    }
  }
  var added = tmp$ret$0;
  if (added)
    return node;
  else if (invokeImmediately) {
    var tmp_1 = this.m1g();
    var tmp56_safe_receiver = tmp_1 instanceof CompletedExceptionally ? tmp_1 : null;
    node.j1j(tmp56_safe_receiver == null ? null : tmp56_safe_receiver.y1f_1);
  }
  return NonDisposableHandle_instance;
};
protoOf(JobSupport).w1g = function (node) {
  // Inline function 'kotlinx.coroutines.JobSupport.loopOnState' call
  while (true) {
    // Inline function 'kotlinx.coroutines.JobSupport.removeNode.<anonymous>' call
    var state = this.m1g();
    if (state instanceof JobNode) {
      if (!(state === node))
        return Unit_instance;
      if (this.m1f_1.atomicfu$compareAndSet(state, get_EMPTY_ACTIVE()))
        return Unit_instance;
    } else {
      if (!(state == null) ? isInterface(state, Incomplete) : false) {
        if (!(state.n1k() == null)) {
          node.s1k();
        }
        return Unit_instance;
      } else {
        return Unit_instance;
      }
    }
  }
};
protoOf(JobSupport).x1g = function () {
  return false;
};
protoOf(JobSupport).w1f = function () {
  return 'Job was cancelled';
};
protoOf(JobSupport).y1g = function (parentJob) {
  this.a1h(parentJob);
};
protoOf(JobSupport).z1g = function (cause) {
  if (cause instanceof CancellationException)
    return true;
  return this.a1h(cause) && this.f1h();
};
protoOf(JobSupport).a1h = function (cause) {
  var finalState = get_COMPLETING_ALREADY();
  if (this.x1g()) {
    finalState = cancelMakeCompleting(this, cause);
    if (finalState === get_COMPLETING_WAITING_CHILDREN())
      return true;
  }
  if (finalState === get_COMPLETING_ALREADY()) {
    finalState = makeCancelling(this, cause);
  }
  var tmp;
  if (finalState === get_COMPLETING_ALREADY()) {
    tmp = true;
  } else if (finalState === get_COMPLETING_WAITING_CHILDREN()) {
    tmp = true;
  } else if (finalState === get_TOO_LATE_TO_CANCEL()) {
    tmp = false;
  } else {
    this.d1g(finalState);
    tmp = true;
  }
  return tmp;
};
protoOf(JobSupport).b1h = function () {
  var state = this.m1g();
  var tmp;
  if (state instanceof Finishing) {
    tmp = state.l1m();
  } else {
    if (state instanceof CompletedExceptionally) {
      tmp = state.y1f_1;
    } else {
      if (!(state == null) ? isInterface(state, Incomplete) : false) {
        var message = 'Cannot be cancelling child in this state: ' + toString(state);
        throw IllegalStateException_init_$Create$(toString(message));
      } else {
        tmp = null;
      }
    }
  }
  var rootCause = tmp;
  var tmp0_elvis_lhs = rootCause instanceof CancellationException ? rootCause : null;
  return tmp0_elvis_lhs == null ? new JobCancellationException('Parent job is ' + stateString(this, state), rootCause, this) : tmp0_elvis_lhs;
};
protoOf(JobSupport).b1g = function (proposedUpdate) {
  // Inline function 'kotlinx.coroutines.JobSupport.loopOnState' call
  while (true) {
    var tmp1 = this.m1g();
    $l$block: {
      // Inline function 'kotlinx.coroutines.JobSupport.makeCompletingOnce.<anonymous>' call
      var finalState = tryMakeCompleting(this, tmp1, proposedUpdate);
      if (finalState === get_COMPLETING_ALREADY())
        throw IllegalStateException_init_$Create$_0('Job ' + this.toString() + ' is already complete or completing, ' + ('but is being completed with ' + toString_0(proposedUpdate)), _get_exceptionOrNull__b3j7js(proposedUpdate, this));
      else if (finalState === get_COMPLETING_RETRY()) {
        break $l$block;
      } else
        return finalState;
    }
  }
};
protoOf(JobSupport).c1h = function (child) {
  // Inline function 'kotlin.also' call
  var this_0 = new ChildHandleNode(child);
  // Inline function 'kotlinx.coroutines.JobSupport.attachChild.<anonymous>' call
  this_0.l1k_1 = this;
  var node = this_0;
  var tmp$ret$1;
  $l$block_1: {
    // Inline function 'kotlinx.coroutines.JobSupport.tryPutNodeIntoList' call
    // Inline function 'kotlinx.coroutines.JobSupport.loopOnState' call
    while (true) {
      // Inline function 'kotlinx.coroutines.JobSupport.tryPutNodeIntoList.<anonymous>' call
      var state = this.m1g();
      if (state instanceof Empty) {
        if (state.v1l_1) {
          if (this.m1f_1.atomicfu$compareAndSet(state, node)) {
            tmp$ret$1 = true;
            break $l$block_1;
          }
        } else {
          promoteEmptyToNodeList(this, state);
        }
      } else {
        if (!(state == null) ? isInterface(state, Incomplete) : false) {
          var list = state.n1k();
          if (list == null) {
            promoteSingleToNodeList(this, state instanceof JobNode ? state : THROW_CCE());
          } else {
            // Inline function 'kotlinx.coroutines.JobSupport.attachChild.<anonymous>' call
            var addedBeforeCancellation = list.r1k(node, 7);
            var tmp;
            if (addedBeforeCancellation) {
              tmp = true;
            } else {
              var addedBeforeCompletion = list.r1k(node, 3);
              var latestState = this.m1g();
              var tmp_0;
              if (latestState instanceof Finishing) {
                tmp_0 = latestState.l1m();
              } else {
                // Inline function 'kotlinx.coroutines.assert' call
                var tmp69_safe_receiver = latestState instanceof CompletedExceptionally ? latestState : null;
                tmp_0 = tmp69_safe_receiver == null ? null : tmp69_safe_receiver.y1f_1;
              }
              var rootCause = tmp_0;
              node.j1j(rootCause);
              var tmp_1;
              if (addedBeforeCompletion) {
                // Inline function 'kotlinx.coroutines.assert' call
                tmp_1 = true;
              } else {
                return NonDisposableHandle_instance;
              }
              tmp = tmp_1;
            }
            if (tmp) {
              tmp$ret$1 = true;
              break $l$block_1;
            }
          }
        } else {
          tmp$ret$1 = false;
          break $l$block_1;
        }
      }
    }
  }
  var added = tmp$ret$1;
  if (added)
    return node;
  var tmp_2 = this.m1g();
  var tmp70_safe_receiver = tmp_2 instanceof CompletedExceptionally ? tmp_2 : null;
  node.j1j(tmp70_safe_receiver == null ? null : tmp70_safe_receiver.y1f_1);
  return NonDisposableHandle_instance;
};
protoOf(JobSupport).e1g = function (exception) {
  throw exception;
};
protoOf(JobSupport).d1h = function (cause) {
};
protoOf(JobSupport).e1h = function () {
  return false;
};
protoOf(JobSupport).f1h = function () {
  return true;
};
protoOf(JobSupport).g1h = function (exception) {
  return false;
};
protoOf(JobSupport).x1f = function (state) {
};
protoOf(JobSupport).d1g = function (state) {
};
protoOf(JobSupport).toString = function () {
  return this.h1h() + '@' + get_hexAddress(this);
};
protoOf(JobSupport).h1h = function () {
  return this.f1g() + '{' + stateString(this, this.m1g()) + '}';
};
protoOf(JobSupport).f1g = function () {
  return get_classSimpleName(this);
};
protoOf(JobSupport).i1h = function () {
  var state = this.m1g();
  // Inline function 'kotlin.check' call
  if (!!(!(state == null) ? isInterface(state, Incomplete) : false)) {
    // Inline function 'kotlinx.coroutines.JobSupport.getCompletionExceptionOrNull.<anonymous>' call
    var message = 'This job has not completed yet';
    throw IllegalStateException_init_$Create$(toString(message));
  }
  return _get_exceptionOrNull__b3j7js(state, this);
};
protoOf(JobSupport).j1h = function () {
  var state = this.m1g();
  // Inline function 'kotlin.check' call
  if (!!(!(state == null) ? isInterface(state, Incomplete) : false)) {
    // Inline function 'kotlinx.coroutines.JobSupport.getCompletedInternal.<anonymous>' call
    var message = 'This job has not completed yet';
    throw IllegalStateException_init_$Create$(toString(message));
  }
  if (state instanceof CompletedExceptionally)
    throw state.y1f_1;
  return unboxState(state);
};
function boxIncomplete(_this__u8e3s4) {
  _init_properties_JobSupport_kt__68f172();
  var tmp;
  if (!(_this__u8e3s4 == null) ? isInterface(_this__u8e3s4, Incomplete) : false) {
    tmp = new IncompleteStateBox(_this__u8e3s4);
  } else {
    tmp = _this__u8e3s4;
  }
  return tmp;
}
function InactiveNodeList(list) {
  this.h1m_1 = list;
}
protoOf(InactiveNodeList).n1k = function () {
  return this.h1m_1;
};
protoOf(InactiveNodeList).t1f = function () {
  return false;
};
protoOf(InactiveNodeList).toString = function () {
  return get_DEBUG() ? this.h1m_1.z1l('New') : anyToString(this);
};
function InvokeOnCompletion(handler) {
  JobNode.call(this);
  this.f1n_1 = handler;
}
protoOf(InvokeOnCompletion).h1k = function () {
  return false;
};
protoOf(InvokeOnCompletion).j1j = function (cause) {
  return this.f1n_1(cause);
};
function InvokeOnCancelling(handler) {
  JobNode.call(this);
  this.k1n_1 = handler;
  this.l1n_1 = atomic$boolean$1(false);
}
protoOf(InvokeOnCancelling).h1k = function () {
  return true;
};
protoOf(InvokeOnCancelling).j1j = function (cause) {
  if (this.l1n_1.atomicfu$compareAndSet(false, true))
    this.k1n_1(cause);
};
function ChildHandleNode(childJob) {
  JobNode.call(this);
  this.r1m_1 = childJob;
}
protoOf(ChildHandleNode).h1k = function () {
  return true;
};
protoOf(ChildHandleNode).j1j = function (cause) {
  return this.r1m_1.y1g(this.m1k());
};
protoOf(ChildHandleNode).z1g = function (cause) {
  return this.m1k().z1g(cause);
};
function unboxState(_this__u8e3s4) {
  _init_properties_JobSupport_kt__68f172();
  var tmp74_safe_receiver = _this__u8e3s4 instanceof IncompleteStateBox ? _this__u8e3s4 : null;
  var tmp0_elvis_lhs = tmp74_safe_receiver == null ? null : tmp74_safe_receiver.m1n_1;
  return tmp0_elvis_lhs == null ? _this__u8e3s4 : tmp0_elvis_lhs;
}
function IncompleteStateBox(state) {
  this.m1n_1 = state;
}
function handlesExceptionF($this) {
  var tmp = $this.l1g();
  var tmp75_safe_receiver = tmp instanceof ChildHandleNode ? tmp : null;
  var tmp0_elvis_lhs = tmp75_safe_receiver == null ? null : tmp75_safe_receiver.m1k();
  var tmp_0;
  if (tmp0_elvis_lhs == null) {
    return false;
  } else {
    tmp_0 = tmp0_elvis_lhs;
  }
  var parentJob = tmp_0;
  while (true) {
    if (parentJob.f1h())
      return true;
    var tmp_1 = parentJob.l1g();
    var tmp76_safe_receiver = tmp_1 instanceof ChildHandleNode ? tmp_1 : null;
    var tmp1_elvis_lhs = tmp76_safe_receiver == null ? null : tmp76_safe_receiver.m1k();
    var tmp_2;
    if (tmp1_elvis_lhs == null) {
      return false;
    } else {
      tmp_2 = tmp1_elvis_lhs;
    }
    parentJob = tmp_2;
  }
}
function JobImpl(parent) {
  JobSupport.call(this, true);
  this.o1f(parent);
  this.p1n_1 = handlesExceptionF(this);
}
protoOf(JobImpl).x1g = function () {
  return true;
};
protoOf(JobImpl).f1h = function () {
  return this.p1n_1;
};
var properties_initialized_JobSupport_kt_5iq8a4;
function _init_properties_JobSupport_kt__68f172() {
  if (!properties_initialized_JobSupport_kt_5iq8a4) {
    properties_initialized_JobSupport_kt_5iq8a4 = true;
    COMPLETING_ALREADY = new Symbol('COMPLETING_ALREADY');
    COMPLETING_WAITING_CHILDREN = new Symbol('COMPLETING_WAITING_CHILDREN');
    COMPLETING_RETRY = new Symbol('COMPLETING_RETRY');
    TOO_LATE_TO_CANCEL = new Symbol('TOO_LATE_TO_CANCEL');
    SEALED = new Symbol('SEALED');
    EMPTY_NEW = new Empty(false);
    EMPTY_ACTIVE = new Empty(true);
  }
}
function MainCoroutineDispatcher() {
  CoroutineDispatcher.call(this);
}
protoOf(MainCoroutineDispatcher).toString = function () {
  var tmp0_elvis_lhs = this.s1n();
  return tmp0_elvis_lhs == null ? get_classSimpleName(this) + '@' + get_hexAddress(this) : tmp0_elvis_lhs;
};
protoOf(MainCoroutineDispatcher).s1n = function () {
  var main = Dispatchers_getInstance().d1l();
  if (this === main)
    return 'Dispatchers.Main';
  var tmp;
  try {
    tmp = main.r1n();
  } catch ($p) {
    var tmp_0;
    if ($p instanceof UnsupportedOperationException) {
      var e = $p;
      tmp_0 = null;
    } else {
      throw $p;
    }
    tmp = tmp_0;
  }
  var immediate = tmp;
  if (this === immediate)
    return 'Dispatchers.Main.immediate';
  return null;
};
function SupervisorJob(parent) {
  parent = parent === VOID ? null : parent;
  return new SupervisorJobImpl(parent);
}
function SupervisorJobImpl(parent) {
  JobImpl.call(this, parent);
}
protoOf(SupervisorJobImpl).z1g = function (cause) {
  return false;
};
function TimeoutCancellationException() {
}
function Unconfined() {
  Unconfined_instance = this;
  CoroutineDispatcher.call(this);
}
protoOf(Unconfined).v1k = function (context) {
  return false;
};
protoOf(Unconfined).w1k = function (context, block) {
  var yieldContext = context.m9(Key_instance_3);
  if (!(yieldContext == null)) {
    yieldContext.y1n_1 = true;
    return Unit_instance;
  }
  throw UnsupportedOperationException_init_$Create$('Dispatchers.Unconfined.dispatch function can only be used by the yield function. If you wrap Unconfined dispatcher in your code, make sure you properly delegate isDispatchNeeded and dispatch calls.');
};
protoOf(Unconfined).toString = function () {
  return 'Dispatchers.Unconfined';
};
var Unconfined_instance;
function Unconfined_getInstance() {
  if (Unconfined_instance == null)
    new Unconfined();
  return Unconfined_instance;
}
function Key_2() {
}
var Key_instance_3;
function Key_getInstance_2() {
  return Key_instance_3;
}
function get_NONE() {
  _init_properties_StateFlow_kt__eu9yi5();
  return NONE;
}
var NONE;
function get_PENDING() {
  _init_properties_StateFlow_kt__eu9yi5();
  return PENDING;
}
var PENDING;
function MutableStateFlow(value) {
  _init_properties_StateFlow_kt__eu9yi5();
  return new StateFlowImpl(value == null ? get_NULL() : value);
}
function updateState($this, expectedState, newState) {
  var curSequence;
  var curSlots;
  // Inline function 'kotlinx.coroutines.internal.synchronized' call
  // Inline function 'kotlinx.coroutines.internal.synchronizedImpl' call
  var oldState = $this.d1o_1.kotlinx$atomicfu$value;
  if (!(expectedState == null) && !equals(oldState, expectedState))
    return false;
  if (equals(oldState, newState))
    return true;
  $this.d1o_1.kotlinx$atomicfu$value = newState;
  curSequence = $this.e1o_1;
  if ((curSequence & 1) === 0) {
    curSequence = curSequence + 1 | 0;
    $this.e1o_1 = curSequence;
  } else {
    $this.e1o_1 = curSequence + 2 | 0;
    return true;
  }
  curSlots = $this.f1o_1;
  while (true) {
    var tmp127_safe_receiver = curSlots;
    if (tmp127_safe_receiver == null)
      null;
    else {
      // Inline function 'kotlin.collections.forEach' call
      var inductionVariable = 0;
      var last = tmp127_safe_receiver.length;
      while (inductionVariable < last) {
        var element = tmp127_safe_receiver[inductionVariable];
        inductionVariable = inductionVariable + 1 | 0;
        // Inline function 'kotlinx.coroutines.flow.StateFlowImpl.updateState.<anonymous>' call
        if (element == null)
          null;
        else {
          element.k1o();
        }
      }
    }
    // Inline function 'kotlinx.coroutines.internal.synchronized' call
    // Inline function 'kotlinx.coroutines.internal.synchronizedImpl' call
    if ($this.e1o_1 === curSequence) {
      $this.e1o_1 = curSequence + 1 | 0;
      return true;
    }
    curSequence = $this.e1o_1;
    curSlots = $this.f1o_1;
  }
}
function StateFlowImpl(initialState) {
  AbstractSharedFlow.call(this);
  this.d1o_1 = atomic$ref$1(initialState);
  this.e1o_1 = 0;
}
protoOf(StateFlowImpl).l1o = function (value) {
  updateState(this, null, value == null ? get_NULL() : value);
};
protoOf(StateFlowImpl).x1 = function () {
  var tmp0 = get_NULL();
  // Inline function 'kotlinx.coroutines.internal.Symbol.unbox' call
  var value = this.d1o_1.kotlinx$atomicfu$value;
  var tmp;
  if (value === tmp0) {
    tmp = (null == null ? true : !(null == null)) ? null : THROW_CCE();
  } else {
    tmp = (value == null ? true : !(value == null)) ? value : THROW_CCE();
  }
  return tmp;
};
var properties_initialized_StateFlow_kt_nsqikx;
function _init_properties_StateFlow_kt__eu9yi5() {
  if (!properties_initialized_StateFlow_kt_nsqikx) {
    properties_initialized_StateFlow_kt_nsqikx = true;
    NONE = new Symbol('NONE');
    PENDING = new Symbol('PENDING');
  }
}
function AbstractSharedFlow() {
  SynchronizedObject.call(this);
  this.f1o_1 = null;
  this.g1o_1 = 0;
  this.h1o_1 = 0;
  this.i1o_1 = null;
}
function get_NULL() {
  _init_properties_NullSurrogate_kt__n2yti9();
  return NULL;
}
var NULL;
var UNINITIALIZED;
var DONE;
var properties_initialized_NullSurrogate_kt_39v8bl;
function _init_properties_NullSurrogate_kt__n2yti9() {
  if (!properties_initialized_NullSurrogate_kt_39v8bl) {
    properties_initialized_NullSurrogate_kt_39v8bl = true;
    NULL = new Symbol('NULL');
    UNINITIALIZED = new Symbol('UNINITIALIZED');
    DONE = new Symbol('DONE');
  }
}
function get_value(_this__u8e3s4) {
  return _this__u8e3s4.t1l();
}
function Segment() {
}
function ConcurrentLinkedListNode() {
}
function handleUncaughtCoroutineException(context, exception) {
  var _iterator__ex2g4s = get_platformExceptionHandlers().j();
  while (_iterator__ex2g4s.k()) {
    var handler = _iterator__ex2g4s.l();
    try {
      handler.y1k(context, exception);
    } catch ($p) {
      if ($p instanceof ExceptionSuccessfullyProcessed) {
        var _unused_var__etf5q3 = $p;
        return Unit_instance;
      } else {
        if ($p instanceof Error) {
          var t = $p;
          propagateExceptionFinalResort(handlerException(exception, t));
        } else {
          throw $p;
        }
      }
    }
  }
  try {
    addSuppressed(exception, new DiagnosticCoroutineContextException(context));
  } catch ($p) {
    if ($p instanceof Error) {
      var e = $p;
    } else {
      throw $p;
    }
  }
  propagateExceptionFinalResort(exception);
}
function ExceptionSuccessfullyProcessed() {
}
function get_UNDEFINED() {
  _init_properties_DispatchedContinuation_kt__tnmqc0();
  return UNDEFINED;
}
var UNDEFINED;
function get_REUSABLE_CLAIMED() {
  _init_properties_DispatchedContinuation_kt__tnmqc0();
  return REUSABLE_CLAIMED;
}
var REUSABLE_CLAIMED;
function _get_reusableCancellableContinuation__9qex09($this) {
  var tmp = $this.m1i_1.kotlinx$atomicfu$value;
  return tmp instanceof CancellableContinuationImpl ? tmp : null;
}
function DispatchedContinuation(dispatcher, continuation) {
  DispatchedTask.call(this, -1);
  this.i1i_1 = dispatcher;
  this.j1i_1 = continuation;
  this.k1i_1 = get_UNDEFINED();
  this.l1i_1 = threadContextElements(this.f9());
  this.m1i_1 = atomic$ref$1(null);
}
protoOf(DispatchedContinuation).f9 = function () {
  return this.j1i_1.f9();
};
protoOf(DispatchedContinuation).n1i = function () {
  return !(this.m1i_1.kotlinx$atomicfu$value == null);
};
protoOf(DispatchedContinuation).n1o = function () {
  // Inline function 'kotlinx.atomicfu.loop' call
  var this_0 = this.m1i_1;
  while (true) {
    // Inline function 'kotlinx.coroutines.internal.DispatchedContinuation.awaitReusability.<anonymous>' call
    if (!(this_0.kotlinx$atomicfu$value === get_REUSABLE_CLAIMED()))
      return Unit_instance;
  }
};
protoOf(DispatchedContinuation).x1k = function () {
  this.n1o();
  var tmp148_safe_receiver = _get_reusableCancellableContinuation__9qex09(this);
  if (tmp148_safe_receiver == null)
    null;
  else {
    tmp148_safe_receiver.a1j();
  }
};
protoOf(DispatchedContinuation).p1j = function (continuation) {
  // Inline function 'kotlinx.atomicfu.loop' call
  var this_0 = this.m1i_1;
  while (true) {
    // Inline function 'kotlinx.coroutines.internal.DispatchedContinuation.tryReleaseClaimedContinuation.<anonymous>' call
    var state = this_0.kotlinx$atomicfu$value;
    if (state === get_REUSABLE_CLAIMED()) {
      if (this.m1i_1.atomicfu$compareAndSet(get_REUSABLE_CLAIMED(), continuation))
        return null;
    } else {
      if (state instanceof Error) {
        // Inline function 'kotlin.require' call
        // Inline function 'kotlin.require' call
        if (!this.m1i_1.atomicfu$compareAndSet(state, null)) {
          // Inline function 'kotlin.require.<anonymous>' call
          var message = 'Failed requirement.';
          throw IllegalArgumentException_init_$Create$(toString(message));
        }
        return state;
      } else {
        // Inline function 'kotlin.error' call
        var message_0 = 'Inconsistent state ' + toString_0(state);
        throw IllegalStateException_init_$Create$(toString(message_0));
      }
    }
  }
};
protoOf(DispatchedContinuation).p1i = function (cause) {
  // Inline function 'kotlinx.atomicfu.loop' call
  var this_0 = this.m1i_1;
  while (true) {
    // Inline function 'kotlinx.coroutines.internal.DispatchedContinuation.postponeCancellation.<anonymous>' call
    var state = this_0.kotlinx$atomicfu$value;
    if (equals(state, get_REUSABLE_CLAIMED())) {
      if (this.m1i_1.atomicfu$compareAndSet(get_REUSABLE_CLAIMED(), cause))
        return true;
    } else {
      if (state instanceof Error)
        return true;
      else {
        if (this.m1i_1.atomicfu$compareAndSet(state, null))
          return false;
      }
    }
  }
};
protoOf(DispatchedContinuation).e1j = function () {
  var state = this.k1i_1;
  // Inline function 'kotlinx.coroutines.assert' call
  this.k1i_1 = get_UNDEFINED();
  return state;
};
protoOf(DispatchedContinuation).b1j = function () {
  return this;
};
protoOf(DispatchedContinuation).k9 = function (result) {
  var state = toState_0(result);
  if (this.i1i_1.v1k(this.f9())) {
    this.k1i_1 = state;
    this.o1i_1 = 0;
    this.i1i_1.w1k(this.f9(), this);
  } else {
    $l$block: {
      // Inline function 'kotlinx.coroutines.internal.executeUnconfined' call
      // Inline function 'kotlinx.coroutines.assert' call
      var eventLoop = ThreadLocalEventLoop_getInstance().r1l();
      if (false && eventLoop.m1l()) {
        break $l$block;
      }
      var tmp;
      if (eventLoop.l1l()) {
        this.k1i_1 = state;
        this.o1i_1 = 0;
        eventLoop.k1l(this);
        tmp = true;
      } else {
        // Inline function 'kotlinx.coroutines.runUnconfinedEventLoop' call
        eventLoop.n1l(true);
        try {
          // Inline function 'kotlinx.coroutines.internal.DispatchedContinuation.resumeWith.<anonymous>' call
          this.f9();
          // Inline function 'kotlinx.coroutines.withCoroutineContext' call
          this.l1i_1;
          // Inline function 'kotlinx.coroutines.internal.DispatchedContinuation.resumeWith.<anonymous>.<anonymous>' call
          this.j1i_1.k9(result);
          $l$loop: while (eventLoop.j1l()) {
          }
        } catch ($p) {
          if ($p instanceof Error) {
            var e = $p;
            this.a1k(e);
          } else {
            throw $p;
          }
        }
        finally {
          eventLoop.o1l(true);
        }
        tmp = false;
      }
    }
  }
};
protoOf(DispatchedContinuation).toString = function () {
  return 'DispatchedContinuation[' + this.i1i_1.toString() + ', ' + toDebugString(this.j1i_1) + ']';
};
function resumeCancellableWith(_this__u8e3s4, result) {
  _init_properties_DispatchedContinuation_kt__tnmqc0();
  var tmp;
  if (_this__u8e3s4 instanceof DispatchedContinuation) {
    var state = toState_0(result);
    if (_this__u8e3s4.i1i_1.v1k(_this__u8e3s4.f9())) {
      _this__u8e3s4.k1i_1 = state;
      _this__u8e3s4.o1i_1 = 1;
      _this__u8e3s4.i1i_1.w1k(_this__u8e3s4.f9(), _this__u8e3s4);
    } else {
      $l$block: {
        // Inline function 'kotlinx.coroutines.internal.executeUnconfined' call
        // Inline function 'kotlinx.coroutines.assert' call
        var eventLoop = ThreadLocalEventLoop_getInstance().r1l();
        if (false && eventLoop.m1l()) {
          break $l$block;
        }
        var tmp_0;
        if (eventLoop.l1l()) {
          _this__u8e3s4.k1i_1 = state;
          _this__u8e3s4.o1i_1 = 1;
          eventLoop.k1l(_this__u8e3s4);
          tmp_0 = true;
        } else {
          // Inline function 'kotlinx.coroutines.runUnconfinedEventLoop' call
          eventLoop.n1l(true);
          try {
            // Inline function 'kotlinx.coroutines.internal.DispatchedContinuation.resumeCancellableWith.<anonymous>' call
            var tmp$ret$3;
            $l$block_0: {
              // Inline function 'kotlinx.coroutines.internal.DispatchedContinuation.resumeCancelled' call
              var job = _this__u8e3s4.f9().m9(Key_instance_2);
              if (!(job == null) && !job.t1f()) {
                var cause = job.q1g();
                _this__u8e3s4.f1j(state, cause);
                // Inline function 'kotlin.coroutines.resumeWithException' call
                // Inline function 'kotlin.Companion.failure' call
                var tmp$ret$1 = _Result___init__impl__xyqfz8(createFailure(cause));
                _this__u8e3s4.k9(tmp$ret$1);
                tmp$ret$3 = true;
                break $l$block_0;
              }
              tmp$ret$3 = false;
            }
            if (!tmp$ret$3) {
              // Inline function 'kotlinx.coroutines.internal.DispatchedContinuation.resumeUndispatchedWith' call
              _this__u8e3s4.j1i_1;
              // Inline function 'kotlinx.coroutines.withContinuationContext' call
              _this__u8e3s4.l1i_1;
              // Inline function 'kotlinx.coroutines.internal.DispatchedContinuation.resumeUndispatchedWith.<anonymous>' call
              _this__u8e3s4.j1i_1.k9(result);
            }
            $l$loop: while (eventLoop.j1l()) {
            }
          } catch ($p) {
            if ($p instanceof Error) {
              var e = $p;
              _this__u8e3s4.a1k(e);
            } else {
              throw $p;
            }
          }
          finally {
            eventLoop.o1l(true);
          }
          tmp_0 = false;
        }
      }
    }
    tmp = Unit_instance;
  } else {
    _this__u8e3s4.k9(result);
    tmp = Unit_instance;
  }
  return tmp;
}
var properties_initialized_DispatchedContinuation_kt_2siadq;
function _init_properties_DispatchedContinuation_kt__tnmqc0() {
  if (!properties_initialized_DispatchedContinuation_kt_2siadq) {
    properties_initialized_DispatchedContinuation_kt_2siadq = true;
    UNDEFINED = new Symbol('UNDEFINED');
    REUSABLE_CLAIMED = new Symbol('REUSABLE_CLAIMED');
  }
}
function DispatchedTask(resumeMode) {
  SchedulerTask.call(this);
  this.o1i_1 = resumeMode;
}
protoOf(DispatchedTask).f1j = function (takenState, cause) {
};
protoOf(DispatchedTask).o1j = function (state) {
  return (state == null ? true : !(state == null)) ? state : THROW_CCE();
};
protoOf(DispatchedTask).x1j = function (state) {
  var tmp151_safe_receiver = state instanceof CompletedExceptionally ? state : null;
  return tmp151_safe_receiver == null ? null : tmp151_safe_receiver.y1f_1;
};
protoOf(DispatchedTask).z1j = function () {
  // Inline function 'kotlinx.coroutines.assert' call
  var fatalException = null;
  try {
    var tmp = this.b1j();
    var delegate = tmp instanceof DispatchedContinuation ? tmp : THROW_CCE();
    var continuation = delegate.j1i_1;
    // Inline function 'kotlinx.coroutines.withContinuationContext' call
    delegate.l1i_1;
    // Inline function 'kotlinx.coroutines.DispatchedTask.run.<anonymous>' call
    var context = continuation.f9();
    var state = this.e1j();
    var exception = this.x1j(state);
    var job = exception == null && get_isCancellableMode(this.o1i_1) ? context.m9(Key_instance_2) : null;
    if (!(job == null) && !job.t1f()) {
      var cause = job.q1g();
      this.f1j(state, cause);
      // Inline function 'kotlinx.coroutines.resumeWithStackTrace' call
      // Inline function 'kotlin.Companion.failure' call
      var exception_0 = recoverStackTrace(cause, continuation);
      var tmp$ret$0 = _Result___init__impl__xyqfz8(createFailure(exception_0));
      continuation.k9(tmp$ret$0);
    } else {
      if (!(exception == null)) {
        // Inline function 'kotlin.coroutines.resumeWithException' call
        // Inline function 'kotlin.Companion.failure' call
        var tmp$ret$1 = _Result___init__impl__xyqfz8(createFailure(exception));
        continuation.k9(tmp$ret$1);
      } else {
        // Inline function 'kotlin.coroutines.resume' call
        // Inline function 'kotlin.Companion.success' call
        var value = this.o1j(state);
        var tmp$ret$3 = _Result___init__impl__xyqfz8(value);
        continuation.k9(tmp$ret$3);
      }
    }
  } catch ($p) {
    if ($p instanceof Error) {
      var e = $p;
      fatalException = e;
    } else {
      throw $p;
    }
  }
  finally {
    var tmp152_safe_receiver = fatalException;
    if (tmp152_safe_receiver == null)
      null;
    else {
      // Inline function 'kotlin.let' call
      // Inline function 'kotlinx.coroutines.DispatchedTask.run.<anonymous>' call
      this.a1k(tmp152_safe_receiver);
    }
  }
};
protoOf(DispatchedTask).a1k = function (exception) {
  var reason = new CoroutinesInternalError('Fatal exception in coroutines machinery for ' + toString(this) + '. ' + "Please read KDoc to 'handleFatalException' method and report this incident to maintainers", exception);
  handleCoroutineException(this.b1j().f9(), reason);
};
function get_isReusableMode(_this__u8e3s4) {
  return _this__u8e3s4 === 2;
}
function get_isCancellableMode(_this__u8e3s4) {
  return _this__u8e3s4 === 1 || _this__u8e3s4 === 2;
}
function dispatch(_this__u8e3s4, mode) {
  // Inline function 'kotlinx.coroutines.assert' call
  var delegate = _this__u8e3s4.b1j();
  var undispatched = mode === 4;
  var tmp;
  var tmp_0;
  if (!undispatched) {
    tmp_0 = delegate instanceof DispatchedContinuation;
  } else {
    tmp_0 = false;
  }
  if (tmp_0) {
    tmp = get_isCancellableMode(mode) === get_isCancellableMode(_this__u8e3s4.o1i_1);
  } else {
    tmp = false;
  }
  if (tmp) {
    var dispatcher = delegate.i1i_1;
    var context = delegate.f9();
    if (dispatcher.v1k(context)) {
      dispatcher.w1k(context, _this__u8e3s4);
    } else {
      resumeUnconfined(_this__u8e3s4);
    }
  } else {
    resume(_this__u8e3s4, delegate, undispatched);
  }
}
function resumeUnconfined(_this__u8e3s4) {
  var eventLoop = ThreadLocalEventLoop_getInstance().r1l();
  if (eventLoop.l1l()) {
    eventLoop.k1l(_this__u8e3s4);
  } else {
    // Inline function 'kotlinx.coroutines.runUnconfinedEventLoop' call
    eventLoop.n1l(true);
    try {
      // Inline function 'kotlinx.coroutines.resumeUnconfined.<anonymous>' call
      resume(_this__u8e3s4, _this__u8e3s4.b1j(), true);
      $l$loop: while (eventLoop.j1l()) {
      }
    } catch ($p) {
      if ($p instanceof Error) {
        var e = $p;
        _this__u8e3s4.a1k(e);
      } else {
        throw $p;
      }
    }
    finally {
      eventLoop.o1l(true);
    }
  }
}
function resume(_this__u8e3s4, delegate, undispatched) {
  var state = _this__u8e3s4.e1j();
  var exception = _this__u8e3s4.x1j(state);
  var tmp;
  if (!(exception == null)) {
    // Inline function 'kotlin.Companion.failure' call
    tmp = _Result___init__impl__xyqfz8(createFailure(exception));
  } else {
    // Inline function 'kotlin.Companion.success' call
    var value = _this__u8e3s4.o1j(state);
    tmp = _Result___init__impl__xyqfz8(value);
  }
  var result = tmp;
  if (undispatched) {
    // Inline function 'kotlinx.coroutines.internal.DispatchedContinuation.resumeUndispatchedWith' call
    var this_0 = delegate instanceof DispatchedContinuation ? delegate : THROW_CCE();
    this_0.j1i_1;
    // Inline function 'kotlinx.coroutines.withContinuationContext' call
    this_0.l1i_1;
    // Inline function 'kotlinx.coroutines.internal.DispatchedContinuation.resumeUndispatchedWith.<anonymous>' call
    this_0.j1i_1.k9(result);
  } else {
    delegate.k9(result);
  }
}
function ContextScope(context) {
  this.o1o_1 = context;
}
protoOf(ContextScope).s1f = function () {
  return this.o1o_1;
};
protoOf(ContextScope).toString = function () {
  return 'CoroutineScope(coroutineContext=' + toString(this.o1o_1) + ')';
};
function Symbol(symbol) {
  this.p1o_1 = symbol;
}
protoOf(Symbol).toString = function () {
  return '<' + this.p1o_1 + '>';
};
function startCoroutineCancellable(_this__u8e3s4, fatalCompletion) {
  var tmp;
  try {
    var tmp_0 = intercepted(_this__u8e3s4);
    // Inline function 'kotlin.Companion.success' call
    var tmp$ret$0 = _Result___init__impl__xyqfz8(Unit_instance);
    resumeCancellableWith(tmp_0, tmp$ret$0);
    tmp = Unit_instance;
  } catch ($p) {
    var tmp_1;
    if ($p instanceof Error) {
      var e = $p;
      dispatcherFailure(fatalCompletion, e);
      tmp_1 = Unit_instance;
    } else {
      throw $p;
    }
    tmp = tmp_1;
  }
  return tmp;
}
function startCoroutineCancellable_0(_this__u8e3s4, receiver, completion) {
  var tmp;
  try {
    var tmp_0 = intercepted(createCoroutineUnintercepted(_this__u8e3s4, receiver, completion));
    // Inline function 'kotlin.Companion.success' call
    var tmp$ret$0 = _Result___init__impl__xyqfz8(Unit_instance);
    resumeCancellableWith(tmp_0, tmp$ret$0);
    tmp = Unit_instance;
  } catch ($p) {
    var tmp_1;
    if ($p instanceof Error) {
      var e = $p;
      dispatcherFailure(completion, e);
      tmp_1 = Unit_instance;
    } else {
      throw $p;
    }
    tmp = tmp_1;
  }
  return tmp;
}
function dispatcherFailure(completion, e) {
  // Inline function 'kotlin.Companion.failure' call
  var tmp$ret$0 = _Result___init__impl__xyqfz8(createFailure(e));
  completion.k9(tmp$ret$0);
  throw e;
}
function startCoroutineUndispatched(_this__u8e3s4, receiver, completion) {
  // Inline function 'kotlinx.coroutines.internal.probeCoroutineCreated' call
  var actualCompletion = completion;
  var tmp;
  try {
    // Inline function 'kotlinx.coroutines.withCoroutineContext' call
    actualCompletion.f9();
    // Inline function 'kotlinx.coroutines.intrinsics.startCoroutineUndispatched.<anonymous>' call
    // Inline function 'kotlinx.coroutines.internal.probeCoroutineResumed' call
    // Inline function 'kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn' call
    tmp = startCoroutineUninterceptedOrReturnNonGeneratorVersion(_this__u8e3s4, receiver, actualCompletion);
  } catch ($p) {
    var tmp_0;
    if ($p instanceof Error) {
      var e = $p;
      // Inline function 'kotlin.coroutines.resumeWithException' call
      // Inline function 'kotlin.Companion.failure' call
      var tmp$ret$4 = _Result___init__impl__xyqfz8(createFailure(e));
      actualCompletion.k9(tmp$ret$4);
      return Unit_instance;
    } else {
      throw $p;
    }
  }
  var value = tmp;
  if (!(value === get_COROUTINE_SUSPENDED())) {
    // Inline function 'kotlin.coroutines.resume' call
    // Inline function 'kotlin.Companion.success' call
    var value_0 = (value == null ? true : !(value == null)) ? value : THROW_CCE();
    var tmp$ret$6 = _Result___init__impl__xyqfz8(value_0);
    actualCompletion.k9(tmp$ret$6);
  }
}
function createDefaultDispatcher() {
  var tmp;
  if (isJsdom()) {
    tmp = NodeDispatcher_getInstance();
  } else {
    var tmp_0;
    var tmp_1;
    if (!(typeof window === 'undefined')) {
      // Inline function 'kotlin.js.asDynamic' call
      tmp_1 = window != null;
    } else {
      tmp_1 = false;
    }
    if (tmp_1) {
      // Inline function 'kotlin.js.asDynamic' call
      tmp_0 = !(typeof window.addEventListener === 'undefined');
    } else {
      tmp_0 = false;
    }
    if (tmp_0) {
      tmp = asCoroutineDispatcher(window);
    } else {
      if (typeof process === 'undefined' || typeof process.nextTick === 'undefined') {
        tmp = SetTimeoutDispatcher_getInstance();
      } else {
        tmp = NodeDispatcher_getInstance();
      }
    }
  }
  return tmp;
}
function isJsdom() {
  return !(typeof navigator === 'undefined') && navigator != null && navigator.userAgent != null && !(typeof navigator.userAgent === 'undefined') && !(typeof navigator.userAgent.match === 'undefined') && navigator.userAgent.match('\\bjsdom\\b');
}
var counter;
function get_DEBUG() {
  return DEBUG;
}
var DEBUG;
function get_classSimpleName(_this__u8e3s4) {
  var tmp0_elvis_lhs = getKClassFromExpression(_this__u8e3s4).ma();
  return tmp0_elvis_lhs == null ? 'Unknown' : tmp0_elvis_lhs;
}
function get_hexAddress(_this__u8e3s4) {
  // Inline function 'kotlin.js.asDynamic' call
  var result = _this__u8e3s4.__debug_counter;
  if (!(typeof result === 'number')) {
    counter = counter + 1 | 0;
    result = counter;
    // Inline function 'kotlin.js.asDynamic' call
    _this__u8e3s4.__debug_counter = result;
  }
  return ((!(result == null) ? typeof result === 'number' : false) ? result : THROW_CCE()).toString();
}
function NodeDispatcher() {
  NodeDispatcher_instance = this;
  SetTimeoutBasedDispatcher.call(this);
}
protoOf(NodeDispatcher).s1o = function () {
  process.nextTick(this.z1o_1.x1o_1);
};
var NodeDispatcher_instance;
function NodeDispatcher_getInstance() {
  if (NodeDispatcher_instance == null)
    new NodeDispatcher();
  return NodeDispatcher_instance;
}
function ScheduledMessageQueue$processQueue$lambda(this$0) {
  return function () {
    this$0.d1p();
    return Unit_instance;
  };
}
function ScheduledMessageQueue(dispatcher) {
  MessageQueue.call(this);
  this.w1o_1 = dispatcher;
  var tmp = this;
  tmp.x1o_1 = ScheduledMessageQueue$processQueue$lambda(this);
}
protoOf(ScheduledMessageQueue).e1p = function () {
  this.w1o_1.s1o();
};
protoOf(ScheduledMessageQueue).f1p = function () {
  setTimeout(this.x1o_1, 0);
};
protoOf(ScheduledMessageQueue).g1p = function (timeout) {
  setTimeout(this.x1o_1, timeout);
};
function w3cSetTimeout(handler, timeout) {
  return setTimeout(handler, timeout);
}
function WindowMessageQueue$lambda(this$0) {
  return function (event) {
    var tmp;
    if (event.source == this$0.s1p_1 && event.data == this$0.t1p_1) {
      event.stopPropagation();
      this$0.d1p();
      tmp = Unit_instance;
    }
    return Unit_instance;
  };
}
function WindowMessageQueue$schedule$lambda(this$0) {
  return function (it) {
    this$0.d1p();
    return Unit_instance;
  };
}
function WindowMessageQueue(window_0) {
  MessageQueue.call(this);
  this.s1p_1 = window_0;
  this.t1p_1 = 'dispatchCoroutine';
  this.s1p_1.addEventListener('message', WindowMessageQueue$lambda(this), true);
}
protoOf(WindowMessageQueue).e1p = function () {
  var tmp = Promise.resolve(Unit_instance);
  tmp.then(WindowMessageQueue$schedule$lambda(this));
};
protoOf(WindowMessageQueue).f1p = function () {
  this.s1p_1.postMessage(this.t1p_1, '*');
};
function w3cSetTimeout_0(window_0, handler, timeout) {
  return setTimeout_0(window_0, handler, timeout);
}
function w3cClearTimeout(window_0, handle) {
  return window_0.clearTimeout(handle);
}
function w3cClearTimeout_0(handle) {
  return clearTimeout(handle);
}
function setTimeout_0(window_0, handler, timeout) {
  return window_0.setTimeout(handler, timeout);
}
function await_0(_this__u8e3s4, $completion) {
  // Inline function 'kotlinx.coroutines.suspendCancellableCoroutine.<anonymous>' call
  var cancellable = new CancellableContinuationImpl(intercepted($completion), 1);
  cancellable.c1j();
  // Inline function 'kotlinx.coroutines.await.<anonymous>' call
  var tmp = await$lambda(cancellable);
  _this__u8e3s4.then(tmp, await$lambda_0(cancellable));
  return cancellable.m1j();
}
function promise(_this__u8e3s4, context, start, block) {
  context = context === VOID ? EmptyCoroutineContext_getInstance() : context;
  start = start === VOID ? CoroutineStart_DEFAULT_getInstance() : start;
  return asPromise(async(_this__u8e3s4, context, start, block));
}
function asPromise(_this__u8e3s4) {
  var promise = new Promise(asPromise$lambda(_this__u8e3s4));
  // Inline function 'kotlin.js.asDynamic' call
  promise.deferred = _this__u8e3s4;
  return promise;
}
function await$lambda($cont) {
  return function (it) {
    // Inline function 'kotlin.coroutines.resume' call
    var this_0 = $cont;
    // Inline function 'kotlin.Companion.success' call
    var tmp$ret$0 = _Result___init__impl__xyqfz8(it);
    this_0.k9(tmp$ret$0);
    return Unit_instance;
  };
}
function await$lambda_0($cont) {
  return function (it) {
    // Inline function 'kotlin.coroutines.resumeWithException' call
    var this_0 = $cont;
    // Inline function 'kotlin.Companion.failure' call
    var tmp$ret$0 = _Result___init__impl__xyqfz8(createFailure(it));
    this_0.k9(tmp$ret$0);
    return Unit_instance;
  };
}
function asPromise$lambda$lambda($this_asPromise, $reject, $resolve) {
  return function (it) {
    var e = $this_asPromise.i1h();
    var tmp;
    if (!(e == null)) {
      tmp = $reject(e);
    } else {
      tmp = $resolve($this_asPromise.v1h());
    }
    return Unit_instance;
  };
}
function asPromise$lambda($this_asPromise) {
  return function (resolve, reject) {
    $this_asPromise.t1g(asPromise$lambda$lambda($this_asPromise, reject, resolve));
    return Unit_instance;
  };
}
function asCoroutineDispatcher(_this__u8e3s4) {
  // Inline function 'kotlin.js.asDynamic' call
  var tmp0_elvis_lhs = _this__u8e3s4.coroutineDispatcher;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    // Inline function 'kotlin.also' call
    var this_0 = new WindowDispatcher(_this__u8e3s4);
    // Inline function 'kotlinx.coroutines.asCoroutineDispatcher.<anonymous>' call
    // Inline function 'kotlin.js.asDynamic' call
    _this__u8e3s4.coroutineDispatcher = this_0;
    tmp = this_0;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  return tmp;
}
function propagateExceptionFinalResort(exception) {
  console.error(exception.toString());
}
function createEventLoop() {
  return new UnconfinedEventLoop();
}
function UnconfinedEventLoop() {
  EventLoop.call(this);
}
protoOf(UnconfinedEventLoop).w1k = function (context, block) {
  unsupported();
};
function unsupported() {
  throw UnsupportedOperationException_init_$Create$('runBlocking event loop is not supported');
}
function SetTimeoutDispatcher() {
  SetTimeoutDispatcher_instance = this;
  SetTimeoutBasedDispatcher.call(this);
}
protoOf(SetTimeoutDispatcher).s1o = function () {
  this.z1o_1.g1p(0);
};
var SetTimeoutDispatcher_instance;
function SetTimeoutDispatcher_getInstance() {
  if (SetTimeoutDispatcher_instance == null)
    new SetTimeoutDispatcher();
  return SetTimeoutDispatcher_instance;
}
function SetTimeoutBasedDispatcher$scheduleResumeAfterDelay$lambda($continuation, this$0) {
  return function () {
    // Inline function 'kotlin.with' call
    // Inline function 'kotlinx.coroutines.SetTimeoutBasedDispatcher.scheduleResumeAfterDelay.<anonymous>.<anonymous>' call
    $continuation.w1j(this$0, Unit_instance);
    return Unit_instance;
  };
}
function SetTimeoutBasedDispatcher() {
  CoroutineDispatcher.call(this);
  this.z1o_1 = new ScheduledMessageQueue(this);
}
protoOf(SetTimeoutBasedDispatcher).w1k = function (context, block) {
  this.z1o_1.o1p(block);
};
protoOf(SetTimeoutBasedDispatcher).e1l = function (timeMillis, continuation) {
  var handle = w3cSetTimeout(SetTimeoutBasedDispatcher$scheduleResumeAfterDelay$lambda(continuation, this), delayToInt(timeMillis));
  invokeOnCancellation(continuation, new ClearTimeout(handle));
};
function MessageQueue() {
  this.a1p_1 = ArrayDeque_init_$Create$();
  this.b1p_1 = 16;
  this.c1p_1 = false;
}
protoOf(MessageQueue).h1p = function (element) {
  return this.a1p_1.e(element);
};
protoOf(MessageQueue).e = function (element) {
  return this.h1p((!(element == null) ? isInterface(element, Runnable) : false) ? element : THROW_CCE());
};
protoOf(MessageQueue).i1p = function (elements) {
  return this.a1p_1.n(elements);
};
protoOf(MessageQueue).n = function (elements) {
  return this.i1p(elements);
};
protoOf(MessageQueue).r = function (index) {
  return this.a1p_1.r(index);
};
protoOf(MessageQueue).j1p = function (element) {
  return this.a1p_1.i2(element);
};
protoOf(MessageQueue).i2 = function (element) {
  if (!(!(element == null) ? isInterface(element, Runnable) : false))
    return false;
  return this.j1p((!(element == null) ? isInterface(element, Runnable) : false) ? element : THROW_CCE());
};
protoOf(MessageQueue).k1p = function (elements) {
  return this.a1p_1.j2(elements);
};
protoOf(MessageQueue).j2 = function (elements) {
  return this.k1p(elements);
};
protoOf(MessageQueue).j4 = function (index) {
  return this.a1p_1.j4(index);
};
protoOf(MessageQueue).l1p = function (index, element) {
  return this.a1p_1.u(index, element);
};
protoOf(MessageQueue).u = function (index, element) {
  return this.l1p(index, (!(element == null) ? isInterface(element, Runnable) : false) ? element : THROW_CCE());
};
protoOf(MessageQueue).m1p = function (element) {
  return this.a1p_1.q(element);
};
protoOf(MessageQueue).q = function (element) {
  if (!(!(element == null) ? isInterface(element, Runnable) : false))
    return false;
  return this.m1p((!(element == null) ? isInterface(element, Runnable) : false) ? element : THROW_CCE());
};
protoOf(MessageQueue).o = function (index) {
  return this.a1p_1.o(index);
};
protoOf(MessageQueue).n1p = function (element) {
  return this.a1p_1.t(element);
};
protoOf(MessageQueue).t = function (element) {
  if (!(!(element == null) ? isInterface(element, Runnable) : false))
    return -1;
  return this.n1p((!(element == null) ? isInterface(element, Runnable) : false) ? element : THROW_CCE());
};
protoOf(MessageQueue).p = function () {
  return this.a1p_1.p();
};
protoOf(MessageQueue).j = function () {
  return this.a1p_1.j();
};
protoOf(MessageQueue).m = function () {
  return this.a1p_1.oe_1;
};
protoOf(MessageQueue).o1p = function (element) {
  this.h1p(element);
  if (!this.c1p_1) {
    this.c1p_1 = true;
    this.e1p();
  }
};
protoOf(MessageQueue).d1p = function () {
  try {
    // Inline function 'kotlin.repeat' call
    var times = this.b1p_1;
    var inductionVariable = 0;
    if (inductionVariable < times)
      do {
        var index = inductionVariable;
        inductionVariable = inductionVariable + 1 | 0;
        // Inline function 'kotlinx.coroutines.MessageQueue.process.<anonymous>' call
        var tmp0_elvis_lhs = removeFirstOrNull(this);
        var tmp;
        if (tmp0_elvis_lhs == null) {
          return Unit_instance;
        } else {
          tmp = tmp0_elvis_lhs;
        }
        var element = tmp;
        element.z1j();
      }
       while (inductionVariable < times);
  }finally {
    if (this.p()) {
      this.c1p_1 = false;
    } else {
      this.f1p();
    }
  }
};
function WindowClearTimeout($outer, handle) {
  this.b1q_1 = $outer;
  ClearTimeout.call(this, handle);
}
protoOf(WindowClearTimeout).d1j = function () {
  w3cClearTimeout(this.b1q_1.d1q_1, this.f1q_1);
};
function WindowDispatcher$scheduleResumeAfterDelay$lambda($continuation, this$0) {
  return function () {
    // Inline function 'kotlin.with' call
    // Inline function 'kotlinx.coroutines.WindowDispatcher.scheduleResumeAfterDelay.<anonymous>.<anonymous>' call
    $continuation.w1j(this$0, Unit_instance);
    return Unit_instance;
  };
}
function WindowDispatcher(window_0) {
  CoroutineDispatcher.call(this);
  this.d1q_1 = window_0;
  this.e1q_1 = new WindowMessageQueue(this.d1q_1);
}
protoOf(WindowDispatcher).w1k = function (context, block) {
  return this.e1q_1.o1p(block);
};
protoOf(WindowDispatcher).e1l = function (timeMillis, continuation) {
  var handle = w3cSetTimeout_0(this.d1q_1, WindowDispatcher$scheduleResumeAfterDelay$lambda(continuation, this), delayToInt(timeMillis));
  invokeOnCancellation(continuation, new WindowClearTimeout(this, handle));
};
function delayToInt(timeMillis) {
  return coerceIn(timeMillis, new Long(0, 0), new Long(2147483647, 0)).f1();
}
function ClearTimeout(handle) {
  this.f1q_1 = handle;
}
protoOf(ClearTimeout).d1j = function () {
  w3cClearTimeout_0(this.f1q_1);
};
protoOf(ClearTimeout).j1j = function (cause) {
  this.d1j();
};
protoOf(ClearTimeout).toString = function () {
  return 'ClearTimeout[' + this.f1q_1 + ']';
};
function toDebugString(_this__u8e3s4) {
  return toString(_this__u8e3s4);
}
function get_DefaultDelay() {
  var tmp = Dispatchers_getInstance().z1k_1;
  return isInterface(tmp, Delay) ? tmp : THROW_CCE();
}
function newCoroutineContext(_this__u8e3s4, context) {
  var combined = _this__u8e3s4.s1f().wg(context);
  return !(combined === Dispatchers_getInstance().z1k_1) && combined.m9(Key_instance) == null ? combined.wg(Dispatchers_getInstance().z1k_1) : combined;
}
function get_coroutineName(_this__u8e3s4) {
  return null;
}
function Dispatchers() {
  Dispatchers_instance = this;
  this.z1k_1 = createDefaultDispatcher();
  this.a1l_1 = Unconfined_getInstance();
  this.b1l_1 = new JsMainDispatcher(this.z1k_1, false);
  this.c1l_1 = null;
}
protoOf(Dispatchers).d1l = function () {
  var tmp0_elvis_lhs = this.c1l_1;
  return tmp0_elvis_lhs == null ? this.b1l_1 : tmp0_elvis_lhs;
};
var Dispatchers_instance;
function Dispatchers_getInstance() {
  if (Dispatchers_instance == null)
    new Dispatchers();
  return Dispatchers_instance;
}
function JsMainDispatcher(delegate, invokeImmediately) {
  MainCoroutineDispatcher.call(this);
  this.h1q_1 = delegate;
  this.i1q_1 = invokeImmediately;
  this.j1q_1 = this.i1q_1 ? this : new JsMainDispatcher(this.h1q_1, true);
}
protoOf(JsMainDispatcher).r1n = function () {
  return this.j1q_1;
};
protoOf(JsMainDispatcher).v1k = function (context) {
  return !this.i1q_1;
};
protoOf(JsMainDispatcher).w1k = function (context, block) {
  return this.h1q_1.w1k(context, block);
};
protoOf(JsMainDispatcher).toString = function () {
  var tmp0_elvis_lhs = this.s1n();
  return tmp0_elvis_lhs == null ? this.h1q_1.toString() : tmp0_elvis_lhs;
};
function JobCancellationException(message, cause, job) {
  CancellationException_init_$Init$(message, cause, this);
  captureStack(this, JobCancellationException);
  this.k1q_1 = job;
}
protoOf(JobCancellationException).toString = function () {
  return protoOf(CancellationException).toString.call(this) + '; job=' + toString(this.k1q_1);
};
protoOf(JobCancellationException).equals = function (other) {
  var tmp;
  if (other === this) {
    tmp = true;
  } else {
    var tmp_0;
    var tmp_1;
    var tmp_2;
    if (other instanceof JobCancellationException) {
      tmp_2 = other.message == this.message;
    } else {
      tmp_2 = false;
    }
    if (tmp_2) {
      tmp_1 = equals(other.k1q_1, this.k1q_1);
    } else {
      tmp_1 = false;
    }
    if (tmp_1) {
      tmp_0 = equals(other.cause, this.cause);
    } else {
      tmp_0 = false;
    }
    tmp = tmp_0;
  }
  return tmp;
};
protoOf(JobCancellationException).hashCode = function () {
  var tmp = imul(imul(getStringHashCode(ensureNotNull(this.message)), 31) + hashCode(this.k1q_1) | 0, 31);
  var tmp0_safe_receiver = this.cause;
  var tmp0_elvis_lhs = tmp0_safe_receiver == null ? null : hashCode(tmp0_safe_receiver);
  return tmp + (tmp0_elvis_lhs == null ? 0 : tmp0_elvis_lhs) | 0;
};
function Runnable() {
}
function SchedulerTask() {
}
function identitySet(expectedSize) {
  return HashSet_init_$Create$(expectedSize);
}
function get_platformExceptionHandlers_() {
  _init_properties_CoroutineExceptionHandlerImpl_kt__37d7wf();
  return platformExceptionHandlers_;
}
var platformExceptionHandlers_;
function get_platformExceptionHandlers() {
  _init_properties_CoroutineExceptionHandlerImpl_kt__37d7wf();
  return get_platformExceptionHandlers_();
}
function DiagnosticCoroutineContextException(context) {
  RuntimeException_init_$Init$_0(toString(context), this);
  captureStack(this, DiagnosticCoroutineContextException);
}
var properties_initialized_CoroutineExceptionHandlerImpl_kt_qhrgvx;
function _init_properties_CoroutineExceptionHandlerImpl_kt__37d7wf() {
  if (!properties_initialized_CoroutineExceptionHandlerImpl_kt_qhrgvx) {
    properties_initialized_CoroutineExceptionHandlerImpl_kt_qhrgvx = true;
    // Inline function 'kotlin.collections.mutableSetOf' call
    platformExceptionHandlers_ = LinkedHashSet_init_$Create$();
  }
}
function LockFreeLinkedListHead() {
  LockFreeLinkedListNode.call(this);
}
function LockFreeLinkedListNode() {
  this.o1k_1 = this;
  this.p1k_1 = this;
  this.q1k_1 = false;
}
protoOf(LockFreeLinkedListNode).r1k = function (node, permissionsBitmask) {
  var prev = this.p1k_1;
  var tmp;
  if (prev instanceof ListClosed) {
    tmp = ((prev.o1q_1 & permissionsBitmask) === 0 && prev.r1k(node, permissionsBitmask));
  } else {
    node.o1k_1 = this;
    node.p1k_1 = prev;
    prev.o1k_1 = node;
    this.p1k_1 = node;
    tmp = true;
  }
  return tmp;
};
protoOf(LockFreeLinkedListNode).a1m = function (forbiddenElementsBit) {
  this.r1k(new ListClosed(forbiddenElementsBit), forbiddenElementsBit);
};
protoOf(LockFreeLinkedListNode).s1k = function () {
  if (this.q1k_1)
    return false;
  var prev = this.p1k_1;
  var next = this.o1k_1;
  prev.o1k_1 = next;
  next.p1k_1 = prev;
  this.q1k_1 = true;
  return true;
};
protoOf(LockFreeLinkedListNode).t1k = function (node) {
  if (!(this.o1k_1 === this))
    return false;
  this.r1k(node, -2147483648);
  return true;
};
function ListClosed(forbiddenElementsBitmask) {
  LockFreeLinkedListNode.call(this);
  this.o1q_1 = forbiddenElementsBitmask;
}
function unwrap(exception) {
  return exception;
}
function recoverStackTrace(exception, continuation) {
  return exception;
}
function SynchronizedObject() {
}
function threadContextElements(context) {
  return 0;
}
function CommonThreadLocal() {
  this.s1l_1 = null;
}
protoOf(CommonThreadLocal).t1l = function () {
  var tmp = this.s1l_1;
  return (tmp == null ? true : !(tmp == null)) ? tmp : THROW_CCE();
};
protoOf(CommonThreadLocal).u1l = function (value) {
  this.s1l_1 = value;
};
function commonThreadLocal(name) {
  return new CommonThreadLocal();
}
//region block: post-declaration
protoOf(JobSupport).wg = plus;
protoOf(JobSupport).m9 = get_0;
protoOf(JobSupport).vg = fold;
protoOf(JobSupport).ug = minusKey_0;
protoOf(CoroutineDispatcher).m9 = get;
protoOf(CoroutineDispatcher).ug = minusKey;
//endregion
//region block: init
Active_instance = new Active();
Key_instance_1 = new Key_0();
GlobalScope_instance = new GlobalScope();
Key_instance_2 = new Key_1();
NonDisposableHandle_instance = new NonDisposableHandle();
Key_instance_3 = new Key_2();
counter = 0;
DEBUG = false;
//endregion
//region block: exports
export {
  await_0 as awaitopqeg9l1dqx0,
  delay as delay2673xnmlkmidc,
  GlobalScope_instance as GlobalScope_instancefw9o755vmweu,
  MutableStateFlow as MutableStateFlow34bfoyvwu8czu,
  CoroutineScope as CoroutineScopefcb5f5dwqcas,
  MainScope as MainScope1gi1r4abhrtmm,
  launch as launch1c91vkjzdi9sd,
  promise as promise1ky6tawqaxbt4,
};
//endregion

//# sourceMappingURL=kotlinx-coroutines-core.mjs.map
