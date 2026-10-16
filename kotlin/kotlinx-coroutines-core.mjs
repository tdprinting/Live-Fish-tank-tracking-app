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
    this.z1d(parentContext.o9(Key_instance_2));
  }
  this.c1e_1 = parentContext.gg(this);
}
protoOf(AbstractCoroutine).h9 = function () {
  return this.c1e_1;
};
protoOf(AbstractCoroutine).d1e = function () {
  return this.c1e_1;
};
protoOf(AbstractCoroutine).e1e = function () {
  return protoOf(JobSupport).e1e.call(this);
};
protoOf(AbstractCoroutine).f1e = function (value) {
};
protoOf(AbstractCoroutine).g1e = function (cause, handled) {
};
protoOf(AbstractCoroutine).h1e = function () {
  return get_classSimpleName(this) + ' was cancelled';
};
protoOf(AbstractCoroutine).i1e = function (state) {
  if (state instanceof CompletedExceptionally) {
    this.g1e(state.j1e_1, state.l1e());
  } else {
    this.f1e((state == null ? true : !(state == null)) ? state : THROW_CCE());
  }
};
protoOf(AbstractCoroutine).m9 = function (result) {
  var state = this.m1e(toState_0(result));
  if (state === get_COMPLETING_WAITING_CHILDREN())
    return Unit_instance;
  this.n1e(state);
};
protoOf(AbstractCoroutine).n1e = function (state) {
  return this.o1e(state);
};
protoOf(AbstractCoroutine).p1e = function (exception) {
  handleCoroutineException(this.c1e_1, exception);
};
protoOf(AbstractCoroutine).q1e = function () {
  var tmp0_elvis_lhs = get_coroutineName(this.c1e_1);
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return protoOf(JobSupport).q1e.call(this);
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var coroutineName = tmp;
  return '"' + coroutineName + '":' + protoOf(JobSupport).q1e.call(this);
};
protoOf(AbstractCoroutine).r1e = function (start, receiver, block) {
  start.u1e(block, receiver, this);
};
function launch(_this__u8e3s4, context, start, block) {
  context = context === VOID ? EmptyCoroutineContext_getInstance() : context;
  start = start === VOID ? CoroutineStart_DEFAULT_getInstance() : start;
  var newContext = newCoroutineContext(_this__u8e3s4, context);
  var coroutine = start.s1f() ? new LazyStandaloneCoroutine(newContext, block) : new StandaloneCoroutine(newContext, true);
  coroutine.r1e(start, coroutine, block);
  return coroutine;
}
function StandaloneCoroutine(parentContext, active) {
  AbstractCoroutine.call(this, parentContext, true, active);
}
protoOf(StandaloneCoroutine).q1f = function (exception) {
  handleCoroutineException(this.c1e_1, exception);
  return true;
};
function LazyStandaloneCoroutine(parentContext, block) {
  StandaloneCoroutine.call(this, parentContext, false);
  this.z1f_1 = createCoroutineUnintercepted(block, this, this);
}
protoOf(LazyStandaloneCoroutine).a1f = function () {
  startCoroutineCancellable(this.z1f_1, this);
};
function invokeOnCancellation(_this__u8e3s4, handler) {
  var tmp;
  if (_this__u8e3s4 instanceof CancellableContinuationImpl) {
    _this__u8e3s4.g1g(handler);
    tmp = Unit_instance;
  } else {
    throw UnsupportedOperationException_init_$Create$('third-party implementation of CancellableContinuation is not supported');
  }
  return tmp;
}
function _get_parentHandle__f8dcex($this) {
  return $this.f1g_1.kotlinx$atomicfu$value;
}
function _get_stateDebugRepresentation__bf18u4($this) {
  var tmp5_subject = $this.x1e();
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
  if (get_isReusableMode($this.o1g_1)) {
    var tmp_0 = $this.b1g_1;
    tmp = (tmp_0 instanceof DispatchedContinuation ? tmp_0 : THROW_CCE()).n1g();
  } else {
    tmp = false;
  }
  return tmp;
}
function cancelLater($this, cause) {
  if (!isReusable($this))
    return false;
  var tmp = $this.b1g_1;
  var dispatched = tmp instanceof DispatchedContinuation ? tmp : THROW_CCE();
  return dispatched.p1g(cause);
}
function callSegmentOnCancellation($this, segment, cause) {
  // Inline function 'kotlinx.coroutines.index' call
  var index = $this.d1g_1.kotlinx$atomicfu$value & 536870911;
  // Inline function 'kotlin.check' call
  if (!!(index === 536870911)) {
    // Inline function 'kotlinx.coroutines.CancellableContinuationImpl.callSegmentOnCancellation.<anonymous>' call
    var message = 'The index for Segment.onCancellation(..) is broken';
    throw IllegalStateException_init_$Create$(toString(message));
  }
  // Inline function 'kotlinx.coroutines.CancellableContinuationImpl.callCancelHandlerSafely' call
  try {
    // Inline function 'kotlinx.coroutines.CancellableContinuationImpl.callSegmentOnCancellation.<anonymous>' call
    segment.q1g(index, cause, $this.h9());
  } catch ($p) {
    if ($p instanceof Error) {
      var ex = $p;
      handleCoroutineException($this.h9(), new CompletionHandlerException('Exception in invokeOnCancellation handler for ' + $this.toString(), ex));
    } else {
      throw $p;
    }
  }
}
function trySuspend($this) {
  // Inline function 'kotlinx.atomicfu.loop' call
  var this_0 = $this.d1g_1;
  while (true) {
    // Inline function 'kotlinx.coroutines.CancellableContinuationImpl.trySuspend.<anonymous>' call
    var cur = this_0.kotlinx$atomicfu$value;
    // Inline function 'kotlinx.coroutines.decision' call
    switch (cur >> 29) {
      case 0:
        // Inline function 'kotlinx.coroutines.index' call

        // Inline function 'kotlinx.coroutines.decisionAndIndex' call

        var tmp$ret$2 = (1 << 29) + (cur & 536870911) | 0;
        if ($this.d1g_1.atomicfu$compareAndSet(cur, tmp$ret$2))
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
  var this_0 = $this.d1g_1;
  while (true) {
    // Inline function 'kotlinx.coroutines.CancellableContinuationImpl.tryResume.<anonymous>' call
    var cur = this_0.kotlinx$atomicfu$value;
    // Inline function 'kotlinx.coroutines.decision' call
    switch (cur >> 29) {
      case 0:
        // Inline function 'kotlinx.coroutines.index' call

        // Inline function 'kotlinx.coroutines.decisionAndIndex' call

        var tmp$ret$2 = (2 << 29) + (cur & 536870911) | 0;
        if ($this.d1g_1.atomicfu$compareAndSet(cur, tmp$ret$2))
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
  var tmp0_elvis_lhs = $this.h9().o9(Key_instance_2);
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return null;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var parent = tmp;
  var handle = invokeOnCompletion(parent, VOID, new ChildContinuation($this));
  $this.f1g_1.atomicfu$compareAndSet(null, handle);
  return handle;
}
function invokeOnCancellationImpl($this, handler) {
  // Inline function 'kotlinx.coroutines.assert' call
  // Inline function 'kotlinx.atomicfu.loop' call
  var this_0 = $this.e1g_1;
  while (true) {
    // Inline function 'kotlinx.coroutines.CancellableContinuationImpl.invokeOnCancellationImpl.<anonymous>' call
    var state = this_0.kotlinx$atomicfu$value;
    if (state instanceof Active) {
      if ($this.e1g_1.atomicfu$compareAndSet(state, handler))
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
          if (!state.z1g()) {
            multipleHandlersError($this, handler, state);
          }
          if (state instanceof CancelledContinuation) {
            var tmp13_safe_receiver = state instanceof CompletedExceptionally ? state : null;
            var cause = tmp13_safe_receiver == null ? null : tmp13_safe_receiver.j1e_1;
            if (isInterface(handler, CancelHandler)) {
              $this.w1g(handler, cause);
            } else {
              var segment = handler instanceof Segment ? handler : THROW_CCE();
              callSegmentOnCancellation($this, segment, cause);
            }
          }
          return Unit_instance;
        } else {
          if (state instanceof CompletedContinuation) {
            if (!(state.s1g_1 == null)) {
              multipleHandlersError($this, handler, state);
            }
            if (handler instanceof Segment)
              return Unit_instance;
            if (!isInterface(handler, CancelHandler))
              THROW_CCE();
            if (state.x1g()) {
              $this.w1g(handler, state.v1g_1);
              return Unit_instance;
            }
            var update = state.y1g(VOID, handler);
            if ($this.e1g_1.atomicfu$compareAndSet(state, update))
              return Unit_instance;
          } else {
            if (handler instanceof Segment)
              return Unit_instance;
            if (!isInterface(handler, CancelHandler))
              THROW_CCE();
            var update_0 = new CompletedContinuation(state, handler);
            if ($this.e1g_1.atomicfu$compareAndSet(state, update_0))
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
    $this.a1h();
  }
}
function CancellableContinuationImpl(delegate, resumeMode) {
  DispatchedTask.call(this, resumeMode);
  this.b1g_1 = delegate;
  // Inline function 'kotlinx.coroutines.assert' call
  this.c1g_1 = this.b1g_1.h9();
  var tmp = this;
  // Inline function 'kotlinx.coroutines.decisionAndIndex' call
  var tmp$ret$0 = (0 << 29) + 536870911 | 0;
  tmp.d1g_1 = atomic$int$1(tmp$ret$0);
  this.e1g_1 = atomic$ref$1(Active_instance);
  this.f1g_1 = atomic$ref$1(null);
}
protoOf(CancellableContinuationImpl).b1h = function () {
  return this.b1g_1;
};
protoOf(CancellableContinuationImpl).h9 = function () {
  return this.c1g_1;
};
protoOf(CancellableContinuationImpl).x1e = function () {
  return this.e1g_1.kotlinx$atomicfu$value;
};
protoOf(CancellableContinuationImpl).y1e = function () {
  var tmp = this.x1e();
  return !(!(tmp == null) ? isInterface(tmp, NotCompleted) : false);
};
protoOf(CancellableContinuationImpl).c1h = function () {
  var tmp0_elvis_lhs = installParentHandle(this);
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return Unit_instance;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var handle = tmp;
  if (this.y1e()) {
    handle.d1h();
    this.f1g_1.kotlinx$atomicfu$value = NonDisposableHandle_instance;
  }
};
protoOf(CancellableContinuationImpl).e1h = function () {
  return this.x1e();
};
protoOf(CancellableContinuationImpl).f1h = function (takenState, cause) {
  var this_0 = this.e1g_1;
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
          if (!!state.x1g()) {
            // Inline function 'kotlinx.coroutines.CancellableContinuationImpl.cancelCompletedResult.<anonymous>.<anonymous>' call
            var message_0 = 'Must be called at most once';
            throw IllegalStateException_init_$Create$(toString(message_0));
          }
          var update = state.y1g(VOID, VOID, VOID, VOID, cause);
          if (this.e1g_1.atomicfu$compareAndSet(state, update)) {
            state.g1h(this, cause);
            return Unit_instance;
          }
        } else {
          if (this.e1g_1.atomicfu$compareAndSet(state, new CompletedContinuation(state, VOID, VOID, VOID, cause))) {
            return Unit_instance;
          }
        }
      }
    }
  }
  return Unit_instance;
};
protoOf(CancellableContinuationImpl).h1h = function (cause) {
  // Inline function 'kotlinx.atomicfu.loop' call
  var this_0 = this.e1g_1;
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
      if (!this.e1g_1.atomicfu$compareAndSet(tmp1, update)) {
        break $l$block;
      }
      if (isInterface(tmp1, CancelHandler)) {
        this.w1g(tmp1, cause);
      } else {
        if (tmp1 instanceof Segment) {
          callSegmentOnCancellation(this, tmp1, cause);
        }
      }
      detachChildIfNonResuable(this);
      dispatchResume(this, this.o1g_1);
      return true;
    }
  }
};
protoOf(CancellableContinuationImpl).i1h = function (cause) {
  if (cancelLater(this, cause))
    return Unit_instance;
  this.h1h(cause);
  detachChildIfNonResuable(this);
};
protoOf(CancellableContinuationImpl).w1g = function (handler, cause) {
  var tmp;
  try {
    handler.j1h(cause);
    tmp = Unit_instance;
  } catch ($p) {
    var tmp_0;
    if ($p instanceof Error) {
      var ex = $p;
      handleCoroutineException(this.h9(), new CompletionHandlerException('Exception in invokeOnCancellation handler for ' + this.toString(), ex));
      tmp_0 = Unit_instance;
    } else {
      throw $p;
    }
    tmp = tmp_0;
  }
  return tmp;
};
protoOf(CancellableContinuationImpl).k1h = function (onCancellation, cause, value) {
  try {
    onCancellation(cause, value, this.h9());
  } catch ($p) {
    if ($p instanceof Error) {
      var ex = $p;
      handleCoroutineException(this.h9(), new CompletionHandlerException('Exception in resume onCancellation handler for ' + this.toString(), ex));
    } else {
      throw $p;
    }
  }
};
protoOf(CancellableContinuationImpl).l1h = function (parent) {
  return parent.b1f();
};
protoOf(CancellableContinuationImpl).m1h = function () {
  var isReusable_0 = isReusable(this);
  if (trySuspend(this)) {
    if (_get_parentHandle__f8dcex(this) == null) {
      installParentHandle(this);
    }
    if (isReusable_0) {
      this.n1h();
    }
    return get_COROUTINE_SUSPENDED();
  }
  if (isReusable_0) {
    this.n1h();
  }
  var state = this.x1e();
  if (state instanceof CompletedExceptionally)
    throw recoverStackTrace(state.j1e_1, this);
  if (get_isCancellableMode(this.o1g_1)) {
    var job = this.h9().o9(Key_instance_2);
    if (!(job == null) && !job.e1e()) {
      var cause = job.b1f();
      this.f1h(state, cause);
      throw recoverStackTrace(cause, this);
    }
  }
  return this.o1h(state);
};
protoOf(CancellableContinuationImpl).n1h = function () {
  var tmp = this.b1g_1;
  var tmp10_safe_receiver = tmp instanceof DispatchedContinuation ? tmp : null;
  var tmp0_elvis_lhs = tmp10_safe_receiver == null ? null : tmp10_safe_receiver.p1h(this);
  var tmp_0;
  if (tmp0_elvis_lhs == null) {
    return Unit_instance;
  } else {
    tmp_0 = tmp0_elvis_lhs;
  }
  var cancellationCause = tmp_0;
  this.a1h();
  this.h1h(cancellationCause);
};
protoOf(CancellableContinuationImpl).m9 = function (result) {
  return this.q1h(toState(result, this), this.o1g_1);
};
protoOf(CancellableContinuationImpl).g1g = function (handler) {
  return invokeOnCancellationImpl(this, handler);
};
protoOf(CancellableContinuationImpl).r1h = function (proposedUpdate, resumeMode, onCancellation) {
  // Inline function 'kotlinx.atomicfu.loop' call
  var this_0 = this.e1g_1;
  while (true) {
    var tmp1 = this_0.kotlinx$atomicfu$value;
    $l$block: {
      // Inline function 'kotlinx.coroutines.CancellableContinuationImpl.resumeImpl.<anonymous>' call
      if (!(tmp1 == null) ? isInterface(tmp1, NotCompleted) : false) {
        var update = resumedState(this, tmp1, proposedUpdate, resumeMode, onCancellation, null);
        if (!this.e1g_1.atomicfu$compareAndSet(tmp1, update)) {
          break $l$block;
        }
        detachChildIfNonResuable(this);
        dispatchResume(this, resumeMode);
        return Unit_instance;
      } else {
        if (tmp1 instanceof CancelledContinuation) {
          if (tmp1.v1h()) {
            if (onCancellation == null)
              null;
            else {
              // Inline function 'kotlin.let' call
              // Inline function 'kotlinx.coroutines.CancellableContinuationImpl.resumeImpl.<anonymous>.<anonymous>' call
              this.k1h(onCancellation, tmp1.j1e_1, proposedUpdate);
            }
            return Unit_instance;
          }
        }
      }
      alreadyResumedError(this, proposedUpdate);
    }
  }
};
protoOf(CancellableContinuationImpl).q1h = function (proposedUpdate, resumeMode, onCancellation, $super) {
  onCancellation = onCancellation === VOID ? null : onCancellation;
  var tmp;
  if ($super === VOID) {
    this.r1h(proposedUpdate, resumeMode, onCancellation);
    tmp = Unit_instance;
  } else {
    tmp = $super.r1h.call(this, proposedUpdate, resumeMode, onCancellation);
  }
  return tmp;
};
protoOf(CancellableContinuationImpl).a1h = function () {
  var tmp0_elvis_lhs = _get_parentHandle__f8dcex(this);
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return Unit_instance;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var handle = tmp;
  handle.d1h();
  this.f1g_1.kotlinx$atomicfu$value = NonDisposableHandle_instance;
};
protoOf(CancellableContinuationImpl).w1h = function (_this__u8e3s4, value) {
  var tmp = this.b1g_1;
  var dc = tmp instanceof DispatchedContinuation ? tmp : null;
  var tmp_0;
  if ((dc == null ? null : dc.i1g_1) === _this__u8e3s4) {
    tmp_0 = 4;
  } else {
    tmp_0 = this.o1g_1;
  }
  this.q1h(value, tmp_0);
};
protoOf(CancellableContinuationImpl).o1h = function (state) {
  var tmp;
  if (state instanceof CompletedContinuation) {
    var tmp_0 = state.r1g_1;
    tmp = (tmp_0 == null ? true : !(tmp_0 == null)) ? tmp_0 : THROW_CCE();
  } else {
    tmp = (state == null ? true : !(state == null)) ? state : THROW_CCE();
  }
  return tmp;
};
protoOf(CancellableContinuationImpl).x1h = function (state) {
  var tmp20_safe_receiver = protoOf(DispatchedTask).x1h.call(this, state);
  var tmp;
  if (tmp20_safe_receiver == null) {
    tmp = null;
  } else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlinx.coroutines.CancellableContinuationImpl.getExceptionalResult.<anonymous>' call
    tmp = recoverStackTrace(tmp20_safe_receiver, this.b1g_1);
  }
  return tmp;
};
protoOf(CancellableContinuationImpl).toString = function () {
  return this.y1h() + '(' + toDebugString(this.b1g_1) + '){' + _get_stateDebugRepresentation__bf18u4(this) + '}@' + get_hexAddress(this);
};
protoOf(CancellableContinuationImpl).y1h = function () {
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
  this.r1g_1 = result;
  this.s1g_1 = cancelHandler;
  this.t1g_1 = onCancellation;
  this.u1g_1 = idempotentResume;
  this.v1g_1 = cancelCause;
}
protoOf(CompletedContinuation).x1g = function () {
  return !(this.v1g_1 == null);
};
protoOf(CompletedContinuation).g1h = function (cont, cause) {
  var tmp21_safe_receiver = this.s1g_1;
  if (tmp21_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlinx.coroutines.CompletedContinuation.invokeHandlers.<anonymous>' call
    cont.w1g(tmp21_safe_receiver, cause);
  }
  var tmp22_safe_receiver = this.t1g_1;
  if (tmp22_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlinx.coroutines.CompletedContinuation.invokeHandlers.<anonymous>' call
    cont.k1h(tmp22_safe_receiver, cause, this.r1g_1);
  }
};
protoOf(CompletedContinuation).b1i = function (result, cancelHandler, onCancellation, idempotentResume, cancelCause) {
  return new CompletedContinuation(result, cancelHandler, onCancellation, idempotentResume, cancelCause);
};
protoOf(CompletedContinuation).y1g = function (result, cancelHandler, onCancellation, idempotentResume, cancelCause, $super) {
  result = result === VOID ? this.r1g_1 : result;
  cancelHandler = cancelHandler === VOID ? this.s1g_1 : cancelHandler;
  onCancellation = onCancellation === VOID ? this.t1g_1 : onCancellation;
  idempotentResume = idempotentResume === VOID ? this.u1g_1 : idempotentResume;
  cancelCause = cancelCause === VOID ? this.v1g_1 : cancelCause;
  return $super === VOID ? this.b1i(result, cancelHandler, onCancellation, idempotentResume, cancelCause) : $super.b1i.call(this, result, cancelHandler, onCancellation, idempotentResume, cancelCause);
};
protoOf(CompletedContinuation).toString = function () {
  return 'CompletedContinuation(result=' + toString_0(this.r1g_1) + ', cancelHandler=' + toString_0(this.s1g_1) + ', onCancellation=' + toString_0(this.t1g_1) + ', idempotentResume=' + toString_0(this.u1g_1) + ', cancelCause=' + toString_0(this.v1g_1) + ')';
};
protoOf(CompletedContinuation).hashCode = function () {
  var result = this.r1g_1 == null ? 0 : hashCode(this.r1g_1);
  result = imul(result, 31) + (this.s1g_1 == null ? 0 : hashCode(this.s1g_1)) | 0;
  result = imul(result, 31) + (this.t1g_1 == null ? 0 : hashCode(this.t1g_1)) | 0;
  result = imul(result, 31) + (this.u1g_1 == null ? 0 : hashCode(this.u1g_1)) | 0;
  result = imul(result, 31) + (this.v1g_1 == null ? 0 : hashCode(this.v1g_1)) | 0;
  return result;
};
protoOf(CompletedContinuation).equals = function (other) {
  if (this === other)
    return true;
  if (!(other instanceof CompletedContinuation))
    return false;
  var tmp0_other_with_cast = other instanceof CompletedContinuation ? other : THROW_CCE();
  if (!equals(this.r1g_1, tmp0_other_with_cast.r1g_1))
    return false;
  if (!equals(this.s1g_1, tmp0_other_with_cast.s1g_1))
    return false;
  if (!equals(this.t1g_1, tmp0_other_with_cast.t1g_1))
    return false;
  if (!equals(this.u1g_1, tmp0_other_with_cast.u1g_1))
    return false;
  if (!equals(this.v1g_1, tmp0_other_with_cast.v1g_1))
    return false;
  return true;
};
function ChildContinuation(child) {
  JobNode.call(this);
  this.g1i_1 = child;
}
protoOf(ChildContinuation).h1i = function () {
  return true;
};
protoOf(ChildContinuation).j1h = function (cause) {
  this.g1i_1.i1h(this.g1i_1.l1h(this.m1i()));
};
function CompletedExceptionally(cause, handled) {
  handled = handled === VOID ? false : handled;
  this.j1e_1 = cause;
  this.k1e_1 = atomic$boolean$1(handled);
}
protoOf(CompletedExceptionally).l1e = function () {
  return this.k1e_1.kotlinx$atomicfu$value;
};
protoOf(CompletedExceptionally).z1g = function () {
  return this.k1e_1.atomicfu$compareAndSet(false, true);
};
protoOf(CompletedExceptionally).toString = function () {
  return get_classSimpleName(this) + '[' + this.j1e_1.toString() + ']';
};
function CancelledContinuation(continuation, cause, handled) {
  CompletedExceptionally.call(this, cause == null ? CancellationException_init_$Create$('Continuation ' + toString(continuation) + ' was cancelled normally') : cause, handled);
  this.u1h_1 = atomic$boolean$1(false);
}
protoOf(CancelledContinuation).v1h = function () {
  return this.u1h_1.atomicfu$compareAndSet(false, true);
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
protoOf(CoroutineDispatcher).v1i = function (context) {
  return true;
};
protoOf(CoroutineDispatcher).p9 = function (continuation) {
  return new DispatchedContinuation(this, continuation);
};
protoOf(CoroutineDispatcher).q9 = function (continuation) {
  var dispatched = continuation instanceof DispatchedContinuation ? continuation : THROW_CCE();
  dispatched.x1i();
};
protoOf(CoroutineDispatcher).toString = function () {
  return get_classSimpleName(this) + '@' + get_hexAddress(this);
};
function handleCoroutineException(context, exception) {
  try {
    var tmp23_safe_receiver = context.o9(Key_instance_1);
    if (tmp23_safe_receiver == null)
      null;
    else {
      // Inline function 'kotlin.let' call
      tmp23_safe_receiver.y1i(context, exception);
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
  return new ContextScope(SupervisorJob().gg(Dispatchers_getInstance().d1j()));
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
protoOf(CoroutineStart).u1e = function (block, receiver, completion) {
  var tmp;
  switch (this.n2_1) {
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
protoOf(CoroutineStart).s1f = function () {
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
  var tmp = _this__u8e3s4.o9(Key_instance);
  var tmp0_elvis_lhs = (!(tmp == null) ? isInterface(tmp, Delay) : false) ? tmp : null;
  return tmp0_elvis_lhs == null ? get_DefaultDelay() : tmp0_elvis_lhs;
}
function delay(timeMillis, $completion) {
  if (timeMillis.d1(new Long(0, 0)) <= 0)
    return Unit_instance;
  // Inline function 'kotlinx.coroutines.suspendCancellableCoroutine.<anonymous>' call
  var cancellable = new CancellableContinuationImpl(intercepted($completion), 1);
  cancellable.c1h();
  // Inline function 'kotlinx.coroutines.delay.<anonymous>' call
  if (timeMillis.d1(new Long(-1, 2147483647)) < 0) {
    get_delay(cancellable.h9()).e1j(timeMillis, cancellable);
  }
  return cancellable.m1h();
}
function delta($this, unconfined) {
  return unconfined ? new Long(0, 1) : new Long(1, 0);
}
function EventLoop() {
  CoroutineDispatcher.call(this);
  this.g1j_1 = new Long(0, 0);
  this.h1j_1 = false;
  this.i1j_1 = null;
}
protoOf(EventLoop).j1j = function () {
  var tmp0_elvis_lhs = this.i1j_1;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    return false;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var queue = tmp;
  var tmp1_elvis_lhs = queue.ke();
  var tmp_0;
  if (tmp1_elvis_lhs == null) {
    return false;
  } else {
    tmp_0 = tmp1_elvis_lhs;
  }
  var task = tmp_0;
  task.z1h();
  return true;
};
protoOf(EventLoop).k1j = function (task) {
  var tmp0_elvis_lhs = this.i1j_1;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    // Inline function 'kotlin.also' call
    var this_0 = ArrayDeque_init_$Create$();
    // Inline function 'kotlinx.coroutines.EventLoop.dispatchUnconfined.<anonymous>' call
    this.i1j_1 = this_0;
    tmp = this_0;
  } else {
    tmp = tmp0_elvis_lhs;
  }
  var queue = tmp;
  queue.ie(task);
};
protoOf(EventLoop).l1j = function () {
  return this.g1j_1.d1(delta(this, true)) >= 0;
};
protoOf(EventLoop).m1j = function () {
  var tmp27_safe_receiver = this.i1j_1;
  var tmp0_elvis_lhs = tmp27_safe_receiver == null ? null : tmp27_safe_receiver.p();
  return tmp0_elvis_lhs == null ? true : tmp0_elvis_lhs;
};
protoOf(EventLoop).n1j = function (unconfined) {
  this.g1j_1 = this.g1j_1.t2(delta(this, unconfined));
  if (!unconfined)
    this.h1j_1 = true;
};
protoOf(EventLoop).o1j = function (unconfined) {
  this.g1j_1 = this.g1j_1.u2(delta(this, unconfined));
  if (this.g1j_1.d1(new Long(0, 0)) > 0)
    return Unit_instance;
  // Inline function 'kotlinx.coroutines.assert' call
  if (this.h1j_1) {
    this.p1j();
  }
};
protoOf(EventLoop).p1j = function () {
};
function ThreadLocalEventLoop() {
  ThreadLocalEventLoop_instance = this;
  this.q1j_1 = commonThreadLocal(new Symbol('ThreadLocalEventLoop'));
}
protoOf(ThreadLocalEventLoop).r1j = function () {
  var tmp0_elvis_lhs = this.q1j_1.t1j();
  var tmp;
  if (tmp0_elvis_lhs == null) {
    // Inline function 'kotlin.also' call
    var this_0 = createEventLoop();
    // Inline function 'kotlinx.coroutines.ThreadLocalEventLoop.<get-eventLoop>.<anonymous>' call
    ThreadLocalEventLoop_getInstance().q1j_1.u1j(this_0);
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
protoOf(NonDisposableHandle).d1h = function () {
};
protoOf(NonDisposableHandle).j1f = function (cause) {
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
    tmp = _this__u8e3s4.f1f(invokeImmediately, handler);
  } else {
    var tmp_0 = handler.h1i();
    tmp = _this__u8e3s4.e1f(tmp_0, invokeImmediately, JobNode$invoke$ref(handler));
  }
  return tmp;
}
function JobNode$invoke$ref($boundThis) {
  var l = function (p0) {
    $boundThis.j1h(p0);
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
  this.v1j_1 = isActive;
}
protoOf(Empty).e1e = function () {
  return this.v1j_1;
};
protoOf(Empty).n1i = function () {
  return null;
};
protoOf(Empty).toString = function () {
  return 'Empty{' + (this.v1j_1 ? 'Active' : 'New') + '}';
};
function Incomplete() {
}
function NodeList() {
  LockFreeLinkedListHead.call(this);
}
protoOf(NodeList).e1e = function () {
  return true;
};
protoOf(NodeList).n1i = function () {
  return this;
};
protoOf(NodeList).z1j = function (state) {
  // Inline function 'kotlin.text.buildString' call
  // Inline function 'kotlin.apply' call
  var this_0 = StringBuilder_init_$Create$();
  // Inline function 'kotlinx.coroutines.NodeList.getString.<anonymous>' call
  this_0.n8('List{');
  this_0.n8(state);
  this_0.n8('}[');
  var first = true;
  // Inline function 'kotlinx.coroutines.internal.LockFreeLinkedListHead.forEach' call
  var cur = this.o1i_1;
  while (!equals(cur, this)) {
    // Inline function 'kotlinx.coroutines.NodeList.getString.<anonymous>.<anonymous>' call
    var node = cur;
    if (node instanceof JobNode) {
      if (first) {
        first = false;
      } else
        this_0.n8(', ');
      this_0.m8(node);
    }
    cur = cur.o1i_1;
  }
  this_0.n8(']');
  return this_0.toString();
};
protoOf(NodeList).toString = function () {
  return get_DEBUG() ? this.z1j('Active') : protoOf(LockFreeLinkedListHead).toString.call(this);
};
function JobNode() {
  LockFreeLinkedListNode.call(this);
}
protoOf(JobNode).m1i = function () {
  var tmp = this.l1i_1;
  if (!(tmp == null))
    return tmp;
  else {
    throwUninitializedPropertyAccessException('job');
  }
};
protoOf(JobNode).e1e = function () {
  return true;
};
protoOf(JobNode).n1i = function () {
  return null;
};
protoOf(JobNode).d1h = function () {
  return this.m1i().g1f(this);
};
protoOf(JobNode).toString = function () {
  return get_classSimpleName(this) + '@' + get_hexAddress(this) + '[job@' + get_hexAddress(this.m1i()) + ']';
};
function _set_exceptionsHolder__tqm22h($this, value) {
  $this.e1k_1.kotlinx$atomicfu$value = value;
}
function _get_exceptionsHolder__nhszp($this) {
  return $this.e1k_1.kotlinx$atomicfu$value;
}
function allocateList($this) {
  return ArrayList_init_$Create$(4);
}
function finalizeFinishingState($this, state, proposedUpdate) {
  // Inline function 'kotlinx.coroutines.assert' call
  // Inline function 'kotlinx.coroutines.assert' call
  // Inline function 'kotlinx.coroutines.assert' call
  var tmp46_safe_receiver = proposedUpdate instanceof CompletedExceptionally ? proposedUpdate : null;
  var proposedException = tmp46_safe_receiver == null ? null : tmp46_safe_receiver.j1e_1;
  var wasCancelling;
  // Inline function 'kotlinx.coroutines.internal.synchronized' call
  // Inline function 'kotlinx.coroutines.internal.synchronizedImpl' call
  // Inline function 'kotlinx.coroutines.JobSupport.finalizeFinishingState.<anonymous>' call
  wasCancelling = state.f1k();
  var exceptions = state.g1k(proposedException);
  var finalCause = getFinalRootCause($this, state, exceptions);
  if (!(finalCause == null)) {
    addSuppressedExceptions($this, finalCause, exceptions);
  }
  var finalException = finalCause;
  var finalState = finalException == null ? proposedUpdate : finalException === proposedException ? proposedUpdate : new CompletedExceptionally(finalException);
  if (!(finalException == null)) {
    var handled = cancelParent($this, finalException) || $this.q1f(finalException);
    if (handled) {
      (finalState instanceof CompletedExceptionally ? finalState : THROW_CCE()).z1g();
    }
  }
  if (!wasCancelling) {
    $this.n1f(finalException);
  }
  $this.i1e(finalState);
  var casSuccess = $this.x1d_1.atomicfu$compareAndSet(state, boxIncomplete(finalState));
  // Inline function 'kotlinx.coroutines.assert' call
  completeStateFinalization($this, state, finalState);
  return finalState;
}
function getFinalRootCause($this, state, exceptions) {
  if (exceptions.p()) {
    if (state.f1k()) {
      // Inline function 'kotlinx.coroutines.JobSupport.defaultCancellationException' call
      return new JobCancellationException(null == null ? $this.h1e() : null, null, $this);
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
  if (!$this.x1d_1.atomicfu$compareAndSet(state, boxIncomplete(update)))
    return false;
  $this.n1f(null);
  $this.i1e(update);
  completeStateFinalization($this, state, update);
  return true;
}
function completeStateFinalization($this, state, update) {
  var tmp47_safe_receiver = $this.w1e();
  if (tmp47_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlinx.coroutines.JobSupport.completeStateFinalization.<anonymous>' call
    tmp47_safe_receiver.d1h();
    $this.v1e(NonDisposableHandle_instance);
  }
  var tmp48_safe_receiver = update instanceof CompletedExceptionally ? update : null;
  var cause = tmp48_safe_receiver == null ? null : tmp48_safe_receiver.j1e_1;
  if (state instanceof JobNode) {
    try {
      state.j1h(cause);
    } catch ($p) {
      if ($p instanceof Error) {
        var ex = $p;
        $this.p1e(new CompletionHandlerException('Exception in completion handler ' + state.toString() + ' for ' + $this.toString(), ex));
      } else {
        throw $p;
      }
    }
  } else {
    var tmp49_safe_receiver = state.n1i();
    if (tmp49_safe_receiver == null)
      null;
    else {
      notifyCompletion(tmp49_safe_receiver, $this, cause);
    }
  }
}
function notifyCancelling($this, list, cause) {
  $this.n1f(cause);
  list.a1k(4);
  // Inline function 'kotlinx.coroutines.JobSupport.notifyHandlers' call
  var exception = null;
  // Inline function 'kotlinx.coroutines.internal.LockFreeLinkedListHead.forEach' call
  var cur = list.o1i_1;
  while (!equals(cur, list)) {
    // Inline function 'kotlinx.coroutines.JobSupport.notifyHandlers.<anonymous>' call
    var node = cur;
    var tmp;
    if (node instanceof JobNode) {
      // Inline function 'kotlinx.coroutines.JobSupport.notifyCancelling.<anonymous>' call
      tmp = node.h1i();
    } else {
      tmp = false;
    }
    if (tmp) {
      try {
        node.j1h(cause);
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
    cur = cur.o1i_1;
  }
  var tmp51_safe_receiver = exception;
  if (tmp51_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlinx.coroutines.JobSupport.notifyHandlers.<anonymous>' call
    $this.p1e(tmp51_safe_receiver);
  }
  cancelParent($this, cause);
}
function cancelParent($this, cause) {
  if ($this.o1f())
    return true;
  var isCancellation = cause instanceof CancellationException;
  var parent = $this.w1e();
  if (parent === null || parent === NonDisposableHandle_instance) {
    return isCancellation;
  }
  return parent.j1f(cause) || isCancellation;
}
function notifyCompletion(_this__u8e3s4, $this, cause) {
  _this__u8e3s4.a1k(1);
  // Inline function 'kotlinx.coroutines.JobSupport.notifyHandlers' call
  var exception = null;
  // Inline function 'kotlinx.coroutines.internal.LockFreeLinkedListHead.forEach' call
  var cur = _this__u8e3s4.o1i_1;
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
        node.j1h(cause);
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
    cur = cur.o1i_1;
  }
  var tmp51_safe_receiver = exception;
  if (tmp51_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlinx.coroutines.JobSupport.notifyHandlers.<anonymous>' call
    $this.p1e(tmp51_safe_receiver);
  }
}
function startInternal($this, state) {
  if (state instanceof Empty) {
    if (state.v1j_1)
      return 0;
    if (!$this.x1d_1.atomicfu$compareAndSet(state, get_EMPTY_ACTIVE()))
      return -1;
    $this.a1f();
    return 1;
  } else {
    if (state instanceof InactiveNodeList) {
      if (!$this.x1d_1.atomicfu$compareAndSet(state, state.h1k_1))
        return -1;
      $this.a1f();
      return 1;
    } else {
      return 0;
    }
  }
}
function promoteEmptyToNodeList($this, state) {
  var list = new NodeList();
  var update = state.v1j_1 ? list : new InactiveNodeList(list);
  $this.x1d_1.atomicfu$compareAndSet(state, update);
}
function promoteSingleToNodeList($this, state) {
  state.t1i(new NodeList());
  // Inline function 'kotlinx.coroutines.internal.LockFreeLinkedListNode.nextNode' call
  var list = state.o1i_1;
  $this.x1d_1.atomicfu$compareAndSet(state, list);
}
function cancelMakeCompleting($this, cause) {
  // Inline function 'kotlinx.coroutines.JobSupport.loopOnState' call
  while (true) {
    // Inline function 'kotlinx.coroutines.JobSupport.cancelMakeCompleting.<anonymous>' call
    var state = $this.x1e();
    var tmp;
    if (!(!(state == null) ? isInterface(state, Incomplete) : false)) {
      tmp = true;
    } else {
      var tmp_0;
      if (state instanceof Finishing) {
        tmp_0 = state.i1k();
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
      tmp_0 = new JobCancellationException(null == null ? $this.h1e() : null, null, $this);
    } else {
      tmp_0 = cause;
    }
    tmp = tmp_0;
  } else {
    tmp = ((!(cause == null) ? isInterface(cause, ParentJob) : false) ? cause : THROW_CCE()).l1f();
  }
  return tmp;
}
function makeCancelling($this, cause) {
  var causeExceptionCache = null;
  // Inline function 'kotlinx.coroutines.JobSupport.loopOnState' call
  while (true) {
    var tmp1 = $this.x1e();
    $l$block: {
      // Inline function 'kotlinx.coroutines.JobSupport.makeCancelling.<anonymous>' call
      if (tmp1 instanceof Finishing) {
        // Inline function 'kotlinx.coroutines.internal.synchronized' call
        // Inline function 'kotlinx.coroutines.internal.synchronizedImpl' call
        // Inline function 'kotlinx.coroutines.JobSupport.makeCancelling.<anonymous>.<anonymous>' call
        if (tmp1.j1k())
          return get_TOO_LATE_TO_CANCEL();
        var wasCancelling = tmp1.f1k();
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
          tmp1.k1k(causeException);
        }
        // Inline function 'kotlin.takeIf' call
        var this_1 = tmp1.l1k();
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
          notifyCancelling($this, tmp1.b1k_1, notifyRootCause);
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
          if (tmp1.e1e()) {
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
  var tmp0_elvis_lhs = state.n1i();
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
  if (!$this.x1d_1.atomicfu$compareAndSet(state, cancelling))
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
  if (finishing.i1k())
    return get_COMPLETING_ALREADY();
  finishing.m1k(true);
  if (!(finishing === state)) {
    if (!$this.x1d_1.atomicfu$compareAndSet(state, finishing))
      return get_COMPLETING_RETRY();
  }
  // Inline function 'kotlinx.coroutines.assert' call
  var wasCancelling = finishing.f1k();
  var tmp65_safe_receiver = proposedUpdate instanceof CompletedExceptionally ? proposedUpdate : null;
  if (tmp65_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlinx.coroutines.JobSupport.tryMakeCompletingSlowPath.<anonymous>.<anonymous>' call
    finishing.k1k(tmp65_safe_receiver.j1e_1);
  }
  // Inline function 'kotlin.takeIf' call
  var this_0 = finishing.l1k();
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
  list.a1k(2);
  var anotherChild = nextChild(list, $this);
  if (!(anotherChild == null) && tryWaitForChild($this, finishing, anotherChild, proposedUpdate))
    return get_COMPLETING_WAITING_CHILDREN();
  return finalizeFinishingState($this, finishing, proposedUpdate);
}
function _get_exceptionOrNull__b3j7js(_this__u8e3s4, $this) {
  var tmp67_safe_receiver = _this__u8e3s4 instanceof CompletedExceptionally ? _this__u8e3s4 : null;
  return tmp67_safe_receiver == null ? null : tmp67_safe_receiver.j1e_1;
}
function tryWaitForChild($this, state, child, proposedUpdate) {
  var $this_0 = $this;
  var state_0 = state;
  var child_0 = child;
  var proposedUpdate_0 = proposedUpdate;
  $l$1: do {
    $l$0: do {
      var handle = invokeOnCompletion(child_0.r1k_1, false, new ChildCompletion($this_0, state_0, child_0, proposedUpdate_0));
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
  state.b1k_1.a1k(2);
  var waitChildAgain = nextChild(lastChild, $this);
  if (!(waitChildAgain == null) && tryWaitForChild($this, state, waitChildAgain, proposedUpdate)) {
    return Unit_instance;
  }
  var finalState = finalizeFinishingState($this, state, proposedUpdate);
  $this.o1e(finalState);
}
function nextChild(_this__u8e3s4, $this) {
  var cur = _this__u8e3s4;
  $l$loop: while (true) {
    // Inline function 'kotlinx.coroutines.internal.LockFreeLinkedListNode.isRemoved' call
    if (!cur.q1i_1) {
      break $l$loop;
    }
    // Inline function 'kotlinx.coroutines.internal.LockFreeLinkedListNode.prevNode' call
    cur = cur.p1i_1;
  }
  $l$loop_0: while (true) {
    // Inline function 'kotlinx.coroutines.internal.LockFreeLinkedListNode.nextNode' call
    cur = cur.o1i_1;
    // Inline function 'kotlinx.coroutines.internal.LockFreeLinkedListNode.isRemoved' call
    if (cur.q1i_1)
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
    tmp = state.f1k() ? 'Cancelling' : state.i1k() ? 'Completing' : 'Active';
  } else {
    if (!(state == null) ? isInterface(state, Incomplete) : false) {
      tmp = state.e1e() ? 'Active' : 'New';
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
  this.b1k_1 = list;
  this.c1k_1 = atomic$boolean$1(isCompleting);
  this.d1k_1 = atomic$ref$1(rootCause);
  this.e1k_1 = atomic$ref$1(null);
}
protoOf(Finishing).n1i = function () {
  return this.b1k_1;
};
protoOf(Finishing).m1k = function (value) {
  this.c1k_1.kotlinx$atomicfu$value = value;
};
protoOf(Finishing).i1k = function () {
  return this.c1k_1.kotlinx$atomicfu$value;
};
protoOf(Finishing).s1k = function (value) {
  this.d1k_1.kotlinx$atomicfu$value = value;
};
protoOf(Finishing).l1k = function () {
  return this.d1k_1.kotlinx$atomicfu$value;
};
protoOf(Finishing).j1k = function () {
  return _get_exceptionsHolder__nhszp(this) === get_SEALED();
};
protoOf(Finishing).f1k = function () {
  return !(this.l1k() == null);
};
protoOf(Finishing).e1e = function () {
  return this.l1k() == null;
};
protoOf(Finishing).g1k = function (proposedException) {
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
  var rootCause = this.l1k();
  if (rootCause == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlinx.coroutines.Finishing.sealLocked.<anonymous>' call
    list.y4(0, rootCause);
  }
  if (!(proposedException == null) && !equals(proposedException, rootCause)) {
    list.e(proposedException);
  }
  _set_exceptionsHolder__tqm22h(this, get_SEALED());
  return list;
};
protoOf(Finishing).k1k = function (exception) {
  var rootCause = this.l1k();
  if (rootCause == null) {
    this.s1k(exception);
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
  return 'Finishing[cancelling=' + this.f1k() + ', completing=' + this.i1k() + ', rootCause=' + toString_0(this.l1k()) + ', exceptions=' + toString_0(_get_exceptionsHolder__nhszp(this)) + ', list=' + this.b1k_1.toString() + ']';
};
function ChildCompletion(parent, state, child, proposedUpdate) {
  JobNode.call(this);
  this.x1k_1 = parent;
  this.y1k_1 = state;
  this.z1k_1 = child;
  this.a1l_1 = proposedUpdate;
}
protoOf(ChildCompletion).h1i = function () {
  return false;
};
protoOf(ChildCompletion).j1h = function (cause) {
  continueCompleting(this.x1k_1, this.y1k_1, this.z1k_1, this.a1l_1);
};
function JobSupport(active) {
  this.x1d_1 = atomic$ref$1(active ? get_EMPTY_ACTIVE() : get_EMPTY_NEW());
  this.y1d_1 = atomic$ref$1(null);
}
protoOf(JobSupport).z1 = function () {
  return Key_instance_2;
};
protoOf(JobSupport).v1e = function (value) {
  this.y1d_1.kotlinx$atomicfu$value = value;
};
protoOf(JobSupport).w1e = function () {
  return this.y1d_1.kotlinx$atomicfu$value;
};
protoOf(JobSupport).z1d = function (parent) {
  // Inline function 'kotlinx.coroutines.assert' call
  if (parent == null) {
    this.v1e(NonDisposableHandle_instance);
    return Unit_instance;
  }
  parent.z1e();
  var handle = parent.m1f(this);
  this.v1e(handle);
  if (this.y1e()) {
    handle.d1h();
    this.v1e(NonDisposableHandle_instance);
  }
};
protoOf(JobSupport).x1e = function () {
  return this.x1d_1.kotlinx$atomicfu$value;
};
protoOf(JobSupport).e1e = function () {
  var state = this.x1e();
  var tmp;
  if (!(state == null) ? isInterface(state, Incomplete) : false) {
    tmp = state.e1e();
  } else {
    tmp = false;
  }
  return tmp;
};
protoOf(JobSupport).y1e = function () {
  var tmp = this.x1e();
  return !(!(tmp == null) ? isInterface(tmp, Incomplete) : false);
};
protoOf(JobSupport).z1e = function () {
  // Inline function 'kotlinx.coroutines.JobSupport.loopOnState' call
  while (true) {
    // Inline function 'kotlinx.coroutines.JobSupport.start.<anonymous>' call
    var state = this.x1e();
    var tmp52_subject = startInternal(this, state);
    if (tmp52_subject === 0)
      return false;
    else if (tmp52_subject === 1)
      return true;
  }
};
protoOf(JobSupport).a1f = function () {
};
protoOf(JobSupport).b1f = function () {
  var state = this.x1e();
  var tmp;
  if (state instanceof Finishing) {
    var tmp54_safe_receiver = state.l1k();
    var tmp0_elvis_lhs = tmp54_safe_receiver == null ? null : this.c1f(tmp54_safe_receiver, get_classSimpleName(this) + ' is cancelling');
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
        tmp = this.d1f(state.j1e_1);
      } else {
        tmp = new JobCancellationException(get_classSimpleName(this) + ' has completed normally', null, this);
      }
    }
  }
  return tmp;
};
protoOf(JobSupport).c1f = function (_this__u8e3s4, message) {
  var tmp0_elvis_lhs = _this__u8e3s4 instanceof CancellationException ? _this__u8e3s4 : null;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    // Inline function 'kotlinx.coroutines.JobSupport.defaultCancellationException' call
    tmp = new JobCancellationException(message == null ? this.h1e() : message, _this__u8e3s4, this);
  } else {
    tmp = tmp0_elvis_lhs;
  }
  return tmp;
};
protoOf(JobSupport).d1f = function (_this__u8e3s4, message, $super) {
  message = message === VOID ? null : message;
  return $super === VOID ? this.c1f(_this__u8e3s4, message) : $super.c1f.call(this, _this__u8e3s4, message);
};
protoOf(JobSupport).e1f = function (onCancelling, invokeImmediately, handler) {
  var tmp;
  if (onCancelling) {
    tmp = new InvokeOnCancelling(handler);
  } else {
    tmp = new InvokeOnCompletion(handler);
  }
  return this.f1f(invokeImmediately, tmp);
};
protoOf(JobSupport).f1f = function (invokeImmediately, node) {
  node.l1i_1 = this;
  var tmp$ret$0;
  $l$block_1: {
    // Inline function 'kotlinx.coroutines.JobSupport.tryPutNodeIntoList' call
    // Inline function 'kotlinx.coroutines.JobSupport.loopOnState' call
    while (true) {
      // Inline function 'kotlinx.coroutines.JobSupport.tryPutNodeIntoList.<anonymous>' call
      var state = this.x1e();
      if (state instanceof Empty) {
        if (state.v1j_1) {
          if (this.x1d_1.atomicfu$compareAndSet(state, node)) {
            tmp$ret$0 = true;
            break $l$block_1;
          }
        } else {
          promoteEmptyToNodeList(this, state);
        }
      } else {
        if (!(state == null) ? isInterface(state, Incomplete) : false) {
          var list = state.n1i();
          if (list == null) {
            promoteSingleToNodeList(this, state instanceof JobNode ? state : THROW_CCE());
          } else {
            // Inline function 'kotlinx.coroutines.JobSupport.invokeOnCompletionInternal.<anonymous>' call
            var tmp;
            if (node.h1i()) {
              var tmp55_safe_receiver = state instanceof Finishing ? state : null;
              var rootCause = tmp55_safe_receiver == null ? null : tmp55_safe_receiver.l1k();
              var tmp_0;
              if (rootCause == null) {
                tmp_0 = list.r1i(node, 5);
              } else {
                if (invokeImmediately) {
                  node.j1h(rootCause);
                }
                return NonDisposableHandle_instance;
              }
              tmp = tmp_0;
            } else {
              tmp = list.r1i(node, 1);
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
    var tmp_1 = this.x1e();
    var tmp56_safe_receiver = tmp_1 instanceof CompletedExceptionally ? tmp_1 : null;
    node.j1h(tmp56_safe_receiver == null ? null : tmp56_safe_receiver.j1e_1);
  }
  return NonDisposableHandle_instance;
};
protoOf(JobSupport).g1f = function (node) {
  // Inline function 'kotlinx.coroutines.JobSupport.loopOnState' call
  while (true) {
    // Inline function 'kotlinx.coroutines.JobSupport.removeNode.<anonymous>' call
    var state = this.x1e();
    if (state instanceof JobNode) {
      if (!(state === node))
        return Unit_instance;
      if (this.x1d_1.atomicfu$compareAndSet(state, get_EMPTY_ACTIVE()))
        return Unit_instance;
    } else {
      if (!(state == null) ? isInterface(state, Incomplete) : false) {
        if (!(state.n1i() == null)) {
          node.s1i();
        }
        return Unit_instance;
      } else {
        return Unit_instance;
      }
    }
  }
};
protoOf(JobSupport).h1f = function () {
  return false;
};
protoOf(JobSupport).h1e = function () {
  return 'Job was cancelled';
};
protoOf(JobSupport).i1f = function (parentJob) {
  this.k1f(parentJob);
};
protoOf(JobSupport).j1f = function (cause) {
  if (cause instanceof CancellationException)
    return true;
  return this.k1f(cause) && this.p1f();
};
protoOf(JobSupport).k1f = function (cause) {
  var finalState = get_COMPLETING_ALREADY();
  if (this.h1f()) {
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
    this.o1e(finalState);
    tmp = true;
  }
  return tmp;
};
protoOf(JobSupport).l1f = function () {
  var state = this.x1e();
  var tmp;
  if (state instanceof Finishing) {
    tmp = state.l1k();
  } else {
    if (state instanceof CompletedExceptionally) {
      tmp = state.j1e_1;
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
protoOf(JobSupport).m1e = function (proposedUpdate) {
  // Inline function 'kotlinx.coroutines.JobSupport.loopOnState' call
  while (true) {
    var tmp1 = this.x1e();
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
protoOf(JobSupport).m1f = function (child) {
  // Inline function 'kotlin.also' call
  var this_0 = new ChildHandleNode(child);
  // Inline function 'kotlinx.coroutines.JobSupport.attachChild.<anonymous>' call
  this_0.l1i_1 = this;
  var node = this_0;
  var tmp$ret$1;
  $l$block_1: {
    // Inline function 'kotlinx.coroutines.JobSupport.tryPutNodeIntoList' call
    // Inline function 'kotlinx.coroutines.JobSupport.loopOnState' call
    while (true) {
      // Inline function 'kotlinx.coroutines.JobSupport.tryPutNodeIntoList.<anonymous>' call
      var state = this.x1e();
      if (state instanceof Empty) {
        if (state.v1j_1) {
          if (this.x1d_1.atomicfu$compareAndSet(state, node)) {
            tmp$ret$1 = true;
            break $l$block_1;
          }
        } else {
          promoteEmptyToNodeList(this, state);
        }
      } else {
        if (!(state == null) ? isInterface(state, Incomplete) : false) {
          var list = state.n1i();
          if (list == null) {
            promoteSingleToNodeList(this, state instanceof JobNode ? state : THROW_CCE());
          } else {
            // Inline function 'kotlinx.coroutines.JobSupport.attachChild.<anonymous>' call
            var addedBeforeCancellation = list.r1i(node, 7);
            var tmp;
            if (addedBeforeCancellation) {
              tmp = true;
            } else {
              var addedBeforeCompletion = list.r1i(node, 3);
              var latestState = this.x1e();
              var tmp_0;
              if (latestState instanceof Finishing) {
                tmp_0 = latestState.l1k();
              } else {
                // Inline function 'kotlinx.coroutines.assert' call
                var tmp69_safe_receiver = latestState instanceof CompletedExceptionally ? latestState : null;
                tmp_0 = tmp69_safe_receiver == null ? null : tmp69_safe_receiver.j1e_1;
              }
              var rootCause = tmp_0;
              node.j1h(rootCause);
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
  var tmp_2 = this.x1e();
  var tmp70_safe_receiver = tmp_2 instanceof CompletedExceptionally ? tmp_2 : null;
  node.j1h(tmp70_safe_receiver == null ? null : tmp70_safe_receiver.j1e_1);
  return NonDisposableHandle_instance;
};
protoOf(JobSupport).p1e = function (exception) {
  throw exception;
};
protoOf(JobSupport).n1f = function (cause) {
};
protoOf(JobSupport).o1f = function () {
  return false;
};
protoOf(JobSupport).p1f = function () {
  return true;
};
protoOf(JobSupport).q1f = function (exception) {
  return false;
};
protoOf(JobSupport).i1e = function (state) {
};
protoOf(JobSupport).o1e = function (state) {
};
protoOf(JobSupport).toString = function () {
  return this.r1f() + '@' + get_hexAddress(this);
};
protoOf(JobSupport).r1f = function () {
  return this.q1e() + '{' + stateString(this, this.x1e()) + '}';
};
protoOf(JobSupport).q1e = function () {
  return get_classSimpleName(this);
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
  this.h1k_1 = list;
}
protoOf(InactiveNodeList).n1i = function () {
  return this.h1k_1;
};
protoOf(InactiveNodeList).e1e = function () {
  return false;
};
protoOf(InactiveNodeList).toString = function () {
  return get_DEBUG() ? this.h1k_1.z1j('New') : anyToString(this);
};
function InvokeOnCompletion(handler) {
  JobNode.call(this);
  this.f1l_1 = handler;
}
protoOf(InvokeOnCompletion).h1i = function () {
  return false;
};
protoOf(InvokeOnCompletion).j1h = function (cause) {
  return this.f1l_1(cause);
};
function InvokeOnCancelling(handler) {
  JobNode.call(this);
  this.k1l_1 = handler;
  this.l1l_1 = atomic$boolean$1(false);
}
protoOf(InvokeOnCancelling).h1i = function () {
  return true;
};
protoOf(InvokeOnCancelling).j1h = function (cause) {
  if (this.l1l_1.atomicfu$compareAndSet(false, true))
    this.k1l_1(cause);
};
function ChildHandleNode(childJob) {
  JobNode.call(this);
  this.r1k_1 = childJob;
}
protoOf(ChildHandleNode).h1i = function () {
  return true;
};
protoOf(ChildHandleNode).j1h = function (cause) {
  return this.r1k_1.i1f(this.m1i());
};
protoOf(ChildHandleNode).j1f = function (cause) {
  return this.m1i().j1f(cause);
};
function IncompleteStateBox(state) {
  this.m1l_1 = state;
}
function handlesExceptionF($this) {
  var tmp = $this.w1e();
  var tmp75_safe_receiver = tmp instanceof ChildHandleNode ? tmp : null;
  var tmp0_elvis_lhs = tmp75_safe_receiver == null ? null : tmp75_safe_receiver.m1i();
  var tmp_0;
  if (tmp0_elvis_lhs == null) {
    return false;
  } else {
    tmp_0 = tmp0_elvis_lhs;
  }
  var parentJob = tmp_0;
  while (true) {
    if (parentJob.p1f())
      return true;
    var tmp_1 = parentJob.w1e();
    var tmp76_safe_receiver = tmp_1 instanceof ChildHandleNode ? tmp_1 : null;
    var tmp1_elvis_lhs = tmp76_safe_receiver == null ? null : tmp76_safe_receiver.m1i();
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
  this.z1d(parent);
  this.p1l_1 = handlesExceptionF(this);
}
protoOf(JobImpl).h1f = function () {
  return true;
};
protoOf(JobImpl).p1f = function () {
  return this.p1l_1;
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
  var tmp0_elvis_lhs = this.s1l();
  return tmp0_elvis_lhs == null ? get_classSimpleName(this) + '@' + get_hexAddress(this) : tmp0_elvis_lhs;
};
protoOf(MainCoroutineDispatcher).s1l = function () {
  var main = Dispatchers_getInstance().d1j();
  if (this === main)
    return 'Dispatchers.Main';
  var tmp;
  try {
    tmp = main.r1l();
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
protoOf(SupervisorJobImpl).j1f = function (cause) {
  return false;
};
function TimeoutCancellationException() {
}
function Unconfined() {
  Unconfined_instance = this;
  CoroutineDispatcher.call(this);
}
protoOf(Unconfined).v1i = function (context) {
  return false;
};
protoOf(Unconfined).w1i = function (context, block) {
  var yieldContext = context.o9(Key_instance_3);
  if (!(yieldContext == null)) {
    yieldContext.y1l_1 = true;
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
  var oldState = $this.d1m_1.kotlinx$atomicfu$value;
  if (!(expectedState == null) && !equals(oldState, expectedState))
    return false;
  if (equals(oldState, newState))
    return true;
  $this.d1m_1.kotlinx$atomicfu$value = newState;
  curSequence = $this.e1m_1;
  if ((curSequence & 1) === 0) {
    curSequence = curSequence + 1 | 0;
    $this.e1m_1 = curSequence;
  } else {
    $this.e1m_1 = curSequence + 2 | 0;
    return true;
  }
  curSlots = $this.f1m_1;
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
          element.k1m();
        }
      }
    }
    // Inline function 'kotlinx.coroutines.internal.synchronized' call
    // Inline function 'kotlinx.coroutines.internal.synchronizedImpl' call
    if ($this.e1m_1 === curSequence) {
      $this.e1m_1 = curSequence + 1 | 0;
      return true;
    }
    curSequence = $this.e1m_1;
    curSlots = $this.f1m_1;
  }
}
function StateFlowImpl(initialState) {
  AbstractSharedFlow.call(this);
  this.d1m_1 = atomic$ref$1(initialState);
  this.e1m_1 = 0;
}
protoOf(StateFlowImpl).l1m = function (value) {
  updateState(this, null, value == null ? get_NULL() : value);
};
protoOf(StateFlowImpl).a2 = function () {
  var tmp0 = get_NULL();
  // Inline function 'kotlinx.coroutines.internal.Symbol.unbox' call
  var value = this.d1m_1.kotlinx$atomicfu$value;
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
  this.f1m_1 = null;
  this.g1m_1 = 0;
  this.h1m_1 = 0;
  this.i1m_1 = null;
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
  return _this__u8e3s4.t1j();
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
      handler.y1i(context, exception);
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
  var tmp = $this.m1g_1.kotlinx$atomicfu$value;
  return tmp instanceof CancellableContinuationImpl ? tmp : null;
}
function DispatchedContinuation(dispatcher, continuation) {
  DispatchedTask.call(this, -1);
  this.i1g_1 = dispatcher;
  this.j1g_1 = continuation;
  this.k1g_1 = get_UNDEFINED();
  this.l1g_1 = threadContextElements(this.h9());
  this.m1g_1 = atomic$ref$1(null);
}
protoOf(DispatchedContinuation).h9 = function () {
  return this.j1g_1.h9();
};
protoOf(DispatchedContinuation).n1g = function () {
  return !(this.m1g_1.kotlinx$atomicfu$value == null);
};
protoOf(DispatchedContinuation).n1m = function () {
  // Inline function 'kotlinx.atomicfu.loop' call
  var this_0 = this.m1g_1;
  while (true) {
    // Inline function 'kotlinx.coroutines.internal.DispatchedContinuation.awaitReusability.<anonymous>' call
    if (!(this_0.kotlinx$atomicfu$value === get_REUSABLE_CLAIMED()))
      return Unit_instance;
  }
};
protoOf(DispatchedContinuation).x1i = function () {
  this.n1m();
  var tmp148_safe_receiver = _get_reusableCancellableContinuation__9qex09(this);
  if (tmp148_safe_receiver == null)
    null;
  else {
    tmp148_safe_receiver.a1h();
  }
};
protoOf(DispatchedContinuation).p1h = function (continuation) {
  // Inline function 'kotlinx.atomicfu.loop' call
  var this_0 = this.m1g_1;
  while (true) {
    // Inline function 'kotlinx.coroutines.internal.DispatchedContinuation.tryReleaseClaimedContinuation.<anonymous>' call
    var state = this_0.kotlinx$atomicfu$value;
    if (state === get_REUSABLE_CLAIMED()) {
      if (this.m1g_1.atomicfu$compareAndSet(get_REUSABLE_CLAIMED(), continuation))
        return null;
    } else {
      if (state instanceof Error) {
        // Inline function 'kotlin.require' call
        // Inline function 'kotlin.require' call
        if (!this.m1g_1.atomicfu$compareAndSet(state, null)) {
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
protoOf(DispatchedContinuation).p1g = function (cause) {
  // Inline function 'kotlinx.atomicfu.loop' call
  var this_0 = this.m1g_1;
  while (true) {
    // Inline function 'kotlinx.coroutines.internal.DispatchedContinuation.postponeCancellation.<anonymous>' call
    var state = this_0.kotlinx$atomicfu$value;
    if (equals(state, get_REUSABLE_CLAIMED())) {
      if (this.m1g_1.atomicfu$compareAndSet(get_REUSABLE_CLAIMED(), cause))
        return true;
    } else {
      if (state instanceof Error)
        return true;
      else {
        if (this.m1g_1.atomicfu$compareAndSet(state, null))
          return false;
      }
    }
  }
};
protoOf(DispatchedContinuation).e1h = function () {
  var state = this.k1g_1;
  // Inline function 'kotlinx.coroutines.assert' call
  this.k1g_1 = get_UNDEFINED();
  return state;
};
protoOf(DispatchedContinuation).b1h = function () {
  return this;
};
protoOf(DispatchedContinuation).m9 = function (result) {
  var state = toState_0(result);
  if (this.i1g_1.v1i(this.h9())) {
    this.k1g_1 = state;
    this.o1g_1 = 0;
    this.i1g_1.w1i(this.h9(), this);
  } else {
    $l$block: {
      // Inline function 'kotlinx.coroutines.internal.executeUnconfined' call
      // Inline function 'kotlinx.coroutines.assert' call
      var eventLoop = ThreadLocalEventLoop_getInstance().r1j();
      if (false && eventLoop.m1j()) {
        break $l$block;
      }
      var tmp;
      if (eventLoop.l1j()) {
        this.k1g_1 = state;
        this.o1g_1 = 0;
        eventLoop.k1j(this);
        tmp = true;
      } else {
        // Inline function 'kotlinx.coroutines.runUnconfinedEventLoop' call
        eventLoop.n1j(true);
        try {
          // Inline function 'kotlinx.coroutines.internal.DispatchedContinuation.resumeWith.<anonymous>' call
          this.h9();
          // Inline function 'kotlinx.coroutines.withCoroutineContext' call
          this.l1g_1;
          // Inline function 'kotlinx.coroutines.internal.DispatchedContinuation.resumeWith.<anonymous>.<anonymous>' call
          this.j1g_1.m9(result);
          $l$loop: while (eventLoop.j1j()) {
          }
        } catch ($p) {
          if ($p instanceof Error) {
            var e = $p;
            this.a1i(e);
          } else {
            throw $p;
          }
        }
        finally {
          eventLoop.o1j(true);
        }
        tmp = false;
      }
    }
  }
};
protoOf(DispatchedContinuation).toString = function () {
  return 'DispatchedContinuation[' + this.i1g_1.toString() + ', ' + toDebugString(this.j1g_1) + ']';
};
function resumeCancellableWith(_this__u8e3s4, result) {
  _init_properties_DispatchedContinuation_kt__tnmqc0();
  var tmp;
  if (_this__u8e3s4 instanceof DispatchedContinuation) {
    var state = toState_0(result);
    if (_this__u8e3s4.i1g_1.v1i(_this__u8e3s4.h9())) {
      _this__u8e3s4.k1g_1 = state;
      _this__u8e3s4.o1g_1 = 1;
      _this__u8e3s4.i1g_1.w1i(_this__u8e3s4.h9(), _this__u8e3s4);
    } else {
      $l$block: {
        // Inline function 'kotlinx.coroutines.internal.executeUnconfined' call
        // Inline function 'kotlinx.coroutines.assert' call
        var eventLoop = ThreadLocalEventLoop_getInstance().r1j();
        if (false && eventLoop.m1j()) {
          break $l$block;
        }
        var tmp_0;
        if (eventLoop.l1j()) {
          _this__u8e3s4.k1g_1 = state;
          _this__u8e3s4.o1g_1 = 1;
          eventLoop.k1j(_this__u8e3s4);
          tmp_0 = true;
        } else {
          // Inline function 'kotlinx.coroutines.runUnconfinedEventLoop' call
          eventLoop.n1j(true);
          try {
            // Inline function 'kotlinx.coroutines.internal.DispatchedContinuation.resumeCancellableWith.<anonymous>' call
            var tmp$ret$3;
            $l$block_0: {
              // Inline function 'kotlinx.coroutines.internal.DispatchedContinuation.resumeCancelled' call
              var job = _this__u8e3s4.h9().o9(Key_instance_2);
              if (!(job == null) && !job.e1e()) {
                var cause = job.b1f();
                _this__u8e3s4.f1h(state, cause);
                // Inline function 'kotlin.coroutines.resumeWithException' call
                // Inline function 'kotlin.Companion.failure' call
                var tmp$ret$1 = _Result___init__impl__xyqfz8(createFailure(cause));
                _this__u8e3s4.m9(tmp$ret$1);
                tmp$ret$3 = true;
                break $l$block_0;
              }
              tmp$ret$3 = false;
            }
            if (!tmp$ret$3) {
              // Inline function 'kotlinx.coroutines.internal.DispatchedContinuation.resumeUndispatchedWith' call
              _this__u8e3s4.j1g_1;
              // Inline function 'kotlinx.coroutines.withContinuationContext' call
              _this__u8e3s4.l1g_1;
              // Inline function 'kotlinx.coroutines.internal.DispatchedContinuation.resumeUndispatchedWith.<anonymous>' call
              _this__u8e3s4.j1g_1.m9(result);
            }
            $l$loop: while (eventLoop.j1j()) {
            }
          } catch ($p) {
            if ($p instanceof Error) {
              var e = $p;
              _this__u8e3s4.a1i(e);
            } else {
              throw $p;
            }
          }
          finally {
            eventLoop.o1j(true);
          }
          tmp_0 = false;
        }
      }
    }
    tmp = Unit_instance;
  } else {
    _this__u8e3s4.m9(result);
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
  this.o1g_1 = resumeMode;
}
protoOf(DispatchedTask).f1h = function (takenState, cause) {
};
protoOf(DispatchedTask).o1h = function (state) {
  return (state == null ? true : !(state == null)) ? state : THROW_CCE();
};
protoOf(DispatchedTask).x1h = function (state) {
  var tmp151_safe_receiver = state instanceof CompletedExceptionally ? state : null;
  return tmp151_safe_receiver == null ? null : tmp151_safe_receiver.j1e_1;
};
protoOf(DispatchedTask).z1h = function () {
  // Inline function 'kotlinx.coroutines.assert' call
  var fatalException = null;
  try {
    var tmp = this.b1h();
    var delegate = tmp instanceof DispatchedContinuation ? tmp : THROW_CCE();
    var continuation = delegate.j1g_1;
    // Inline function 'kotlinx.coroutines.withContinuationContext' call
    delegate.l1g_1;
    // Inline function 'kotlinx.coroutines.DispatchedTask.run.<anonymous>' call
    var context = continuation.h9();
    var state = this.e1h();
    var exception = this.x1h(state);
    var job = exception == null && get_isCancellableMode(this.o1g_1) ? context.o9(Key_instance_2) : null;
    if (!(job == null) && !job.e1e()) {
      var cause = job.b1f();
      this.f1h(state, cause);
      // Inline function 'kotlinx.coroutines.resumeWithStackTrace' call
      // Inline function 'kotlin.Companion.failure' call
      var exception_0 = recoverStackTrace(cause, continuation);
      var tmp$ret$0 = _Result___init__impl__xyqfz8(createFailure(exception_0));
      continuation.m9(tmp$ret$0);
    } else {
      if (!(exception == null)) {
        // Inline function 'kotlin.coroutines.resumeWithException' call
        // Inline function 'kotlin.Companion.failure' call
        var tmp$ret$1 = _Result___init__impl__xyqfz8(createFailure(exception));
        continuation.m9(tmp$ret$1);
      } else {
        // Inline function 'kotlin.coroutines.resume' call
        // Inline function 'kotlin.Companion.success' call
        var value = this.o1h(state);
        var tmp$ret$3 = _Result___init__impl__xyqfz8(value);
        continuation.m9(tmp$ret$3);
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
      this.a1i(tmp152_safe_receiver);
    }
  }
};
protoOf(DispatchedTask).a1i = function (exception) {
  var reason = new CoroutinesInternalError('Fatal exception in coroutines machinery for ' + toString(this) + '. ' + "Please read KDoc to 'handleFatalException' method and report this incident to maintainers", exception);
  handleCoroutineException(this.b1h().h9(), reason);
};
function get_isReusableMode(_this__u8e3s4) {
  return _this__u8e3s4 === 2;
}
function get_isCancellableMode(_this__u8e3s4) {
  return _this__u8e3s4 === 1 || _this__u8e3s4 === 2;
}
function dispatch(_this__u8e3s4, mode) {
  // Inline function 'kotlinx.coroutines.assert' call
  var delegate = _this__u8e3s4.b1h();
  var undispatched = mode === 4;
  var tmp;
  var tmp_0;
  if (!undispatched) {
    tmp_0 = delegate instanceof DispatchedContinuation;
  } else {
    tmp_0 = false;
  }
  if (tmp_0) {
    tmp = get_isCancellableMode(mode) === get_isCancellableMode(_this__u8e3s4.o1g_1);
  } else {
    tmp = false;
  }
  if (tmp) {
    var dispatcher = delegate.i1g_1;
    var context = delegate.h9();
    if (dispatcher.v1i(context)) {
      dispatcher.w1i(context, _this__u8e3s4);
    } else {
      resumeUnconfined(_this__u8e3s4);
    }
  } else {
    resume(_this__u8e3s4, delegate, undispatched);
  }
}
function resumeUnconfined(_this__u8e3s4) {
  var eventLoop = ThreadLocalEventLoop_getInstance().r1j();
  if (eventLoop.l1j()) {
    eventLoop.k1j(_this__u8e3s4);
  } else {
    // Inline function 'kotlinx.coroutines.runUnconfinedEventLoop' call
    eventLoop.n1j(true);
    try {
      // Inline function 'kotlinx.coroutines.resumeUnconfined.<anonymous>' call
      resume(_this__u8e3s4, _this__u8e3s4.b1h(), true);
      $l$loop: while (eventLoop.j1j()) {
      }
    } catch ($p) {
      if ($p instanceof Error) {
        var e = $p;
        _this__u8e3s4.a1i(e);
      } else {
        throw $p;
      }
    }
    finally {
      eventLoop.o1j(true);
    }
  }
}
function resume(_this__u8e3s4, delegate, undispatched) {
  var state = _this__u8e3s4.e1h();
  var exception = _this__u8e3s4.x1h(state);
  var tmp;
  if (!(exception == null)) {
    // Inline function 'kotlin.Companion.failure' call
    tmp = _Result___init__impl__xyqfz8(createFailure(exception));
  } else {
    // Inline function 'kotlin.Companion.success' call
    var value = _this__u8e3s4.o1h(state);
    tmp = _Result___init__impl__xyqfz8(value);
  }
  var result = tmp;
  if (undispatched) {
    // Inline function 'kotlinx.coroutines.internal.DispatchedContinuation.resumeUndispatchedWith' call
    var this_0 = delegate instanceof DispatchedContinuation ? delegate : THROW_CCE();
    this_0.j1g_1;
    // Inline function 'kotlinx.coroutines.withContinuationContext' call
    this_0.l1g_1;
    // Inline function 'kotlinx.coroutines.internal.DispatchedContinuation.resumeUndispatchedWith.<anonymous>' call
    this_0.j1g_1.m9(result);
  } else {
    delegate.m9(result);
  }
}
function ContextScope(context) {
  this.o1m_1 = context;
}
protoOf(ContextScope).d1e = function () {
  return this.o1m_1;
};
protoOf(ContextScope).toString = function () {
  return 'CoroutineScope(coroutineContext=' + toString(this.o1m_1) + ')';
};
function Symbol(symbol) {
  this.p1m_1 = symbol;
}
protoOf(Symbol).toString = function () {
  return '<' + this.p1m_1 + '>';
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
  completion.m9(tmp$ret$0);
  throw e;
}
function startCoroutineUndispatched(_this__u8e3s4, receiver, completion) {
  // Inline function 'kotlinx.coroutines.internal.probeCoroutineCreated' call
  var actualCompletion = completion;
  var tmp;
  try {
    // Inline function 'kotlinx.coroutines.withCoroutineContext' call
    actualCompletion.h9();
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
      actualCompletion.m9(tmp$ret$4);
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
    actualCompletion.m9(tmp$ret$6);
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
  var tmp0_elvis_lhs = getKClassFromExpression(_this__u8e3s4).oa();
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
protoOf(NodeDispatcher).s1m = function () {
  process.nextTick(this.z1m_1.x1m_1);
};
var NodeDispatcher_instance;
function NodeDispatcher_getInstance() {
  if (NodeDispatcher_instance == null)
    new NodeDispatcher();
  return NodeDispatcher_instance;
}
function ScheduledMessageQueue$processQueue$lambda(this$0) {
  return function () {
    this$0.d1n();
    return Unit_instance;
  };
}
function ScheduledMessageQueue(dispatcher) {
  MessageQueue.call(this);
  this.w1m_1 = dispatcher;
  var tmp = this;
  tmp.x1m_1 = ScheduledMessageQueue$processQueue$lambda(this);
}
protoOf(ScheduledMessageQueue).e1n = function () {
  this.w1m_1.s1m();
};
protoOf(ScheduledMessageQueue).f1n = function () {
  setTimeout(this.x1m_1, 0);
};
protoOf(ScheduledMessageQueue).g1n = function (timeout) {
  setTimeout(this.x1m_1, timeout);
};
function w3cSetTimeout(handler, timeout) {
  return setTimeout(handler, timeout);
}
function WindowMessageQueue$lambda(this$0) {
  return function (event) {
    var tmp;
    if (event.source == this$0.r1n_1 && event.data == this$0.s1n_1) {
      event.stopPropagation();
      this$0.d1n();
      tmp = Unit_instance;
    }
    return Unit_instance;
  };
}
function WindowMessageQueue$schedule$lambda(this$0) {
  return function (it) {
    this$0.d1n();
    return Unit_instance;
  };
}
function WindowMessageQueue(window_0) {
  MessageQueue.call(this);
  this.r1n_1 = window_0;
  this.s1n_1 = 'dispatchCoroutine';
  this.r1n_1.addEventListener('message', WindowMessageQueue$lambda(this), true);
}
protoOf(WindowMessageQueue).e1n = function () {
  var tmp = Promise.resolve(Unit_instance);
  tmp.then(WindowMessageQueue$schedule$lambda(this));
};
protoOf(WindowMessageQueue).f1n = function () {
  this.r1n_1.postMessage(this.s1n_1, '*');
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
  cancellable.c1h();
  // Inline function 'kotlinx.coroutines.await.<anonymous>' call
  var tmp = await$lambda(cancellable);
  _this__u8e3s4.then(tmp, await$lambda_0(cancellable));
  return cancellable.m1h();
}
function await$lambda($cont) {
  return function (it) {
    // Inline function 'kotlin.coroutines.resume' call
    var this_0 = $cont;
    // Inline function 'kotlin.Companion.success' call
    var tmp$ret$0 = _Result___init__impl__xyqfz8(it);
    this_0.m9(tmp$ret$0);
    return Unit_instance;
  };
}
function await$lambda_0($cont) {
  return function (it) {
    // Inline function 'kotlin.coroutines.resumeWithException' call
    var this_0 = $cont;
    // Inline function 'kotlin.Companion.failure' call
    var tmp$ret$0 = _Result___init__impl__xyqfz8(createFailure(it));
    this_0.m9(tmp$ret$0);
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
protoOf(UnconfinedEventLoop).w1i = function (context, block) {
  unsupported();
};
function unsupported() {
  throw UnsupportedOperationException_init_$Create$('runBlocking event loop is not supported');
}
function SetTimeoutDispatcher() {
  SetTimeoutDispatcher_instance = this;
  SetTimeoutBasedDispatcher.call(this);
}
protoOf(SetTimeoutDispatcher).s1m = function () {
  this.z1m_1.g1n(0);
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
    $continuation.w1h(this$0, Unit_instance);
    return Unit_instance;
  };
}
function SetTimeoutBasedDispatcher() {
  CoroutineDispatcher.call(this);
  this.z1m_1 = new ScheduledMessageQueue(this);
}
protoOf(SetTimeoutBasedDispatcher).w1i = function (context, block) {
  this.z1m_1.n1n(block);
};
protoOf(SetTimeoutBasedDispatcher).e1j = function (timeMillis, continuation) {
  var handle = w3cSetTimeout(SetTimeoutBasedDispatcher$scheduleResumeAfterDelay$lambda(continuation, this), delayToInt(timeMillis));
  invokeOnCancellation(continuation, new ClearTimeout(handle));
};
function MessageQueue() {
  this.a1n_1 = ArrayDeque_init_$Create$();
  this.b1n_1 = 16;
  this.c1n_1 = false;
}
protoOf(MessageQueue).h1n = function (element) {
  return this.a1n_1.e(element);
};
protoOf(MessageQueue).e = function (element) {
  return this.h1n((!(element == null) ? isInterface(element, Runnable) : false) ? element : THROW_CCE());
};
protoOf(MessageQueue).i1n = function (elements) {
  return this.a1n_1.n(elements);
};
protoOf(MessageQueue).n = function (elements) {
  return this.i1n(elements);
};
protoOf(MessageQueue).k2 = function () {
  this.a1n_1.k2();
};
protoOf(MessageQueue).r = function (index) {
  return this.a1n_1.r(index);
};
protoOf(MessageQueue).j1n = function (element) {
  return this.a1n_1.l2(element);
};
protoOf(MessageQueue).l2 = function (element) {
  if (!(!(element == null) ? isInterface(element, Runnable) : false))
    return false;
  return this.j1n((!(element == null) ? isInterface(element, Runnable) : false) ? element : THROW_CCE());
};
protoOf(MessageQueue).l4 = function (index) {
  return this.a1n_1.l4(index);
};
protoOf(MessageQueue).k1n = function (index, element) {
  return this.a1n_1.u(index, element);
};
protoOf(MessageQueue).u = function (index, element) {
  return this.k1n(index, (!(element == null) ? isInterface(element, Runnable) : false) ? element : THROW_CCE());
};
protoOf(MessageQueue).l1n = function (element) {
  return this.a1n_1.q(element);
};
protoOf(MessageQueue).q = function (element) {
  if (!(!(element == null) ? isInterface(element, Runnable) : false))
    return false;
  return this.l1n((!(element == null) ? isInterface(element, Runnable) : false) ? element : THROW_CCE());
};
protoOf(MessageQueue).o = function (index) {
  return this.a1n_1.o(index);
};
protoOf(MessageQueue).m1n = function (element) {
  return this.a1n_1.t(element);
};
protoOf(MessageQueue).t = function (element) {
  if (!(!(element == null) ? isInterface(element, Runnable) : false))
    return -1;
  return this.m1n((!(element == null) ? isInterface(element, Runnable) : false) ? element : THROW_CCE());
};
protoOf(MessageQueue).p = function () {
  return this.a1n_1.p();
};
protoOf(MessageQueue).j = function () {
  return this.a1n_1.j();
};
protoOf(MessageQueue).m = function () {
  return this.a1n_1.ee_1;
};
protoOf(MessageQueue).n1n = function (element) {
  this.h1n(element);
  if (!this.c1n_1) {
    this.c1n_1 = true;
    this.e1n();
  }
};
protoOf(MessageQueue).d1n = function () {
  try {
    // Inline function 'kotlin.repeat' call
    var times = this.b1n_1;
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
        element.z1h();
      }
       while (inductionVariable < times);
  }finally {
    if (this.p()) {
      this.c1n_1 = false;
    } else {
      this.f1n();
    }
  }
};
function WindowClearTimeout($outer, handle) {
  this.a1o_1 = $outer;
  ClearTimeout.call(this, handle);
}
protoOf(WindowClearTimeout).d1h = function () {
  w3cClearTimeout(this.a1o_1.c1o_1, this.e1o_1);
};
function WindowDispatcher$scheduleResumeAfterDelay$lambda($continuation, this$0) {
  return function () {
    // Inline function 'kotlin.with' call
    // Inline function 'kotlinx.coroutines.WindowDispatcher.scheduleResumeAfterDelay.<anonymous>.<anonymous>' call
    $continuation.w1h(this$0, Unit_instance);
    return Unit_instance;
  };
}
function WindowDispatcher(window_0) {
  CoroutineDispatcher.call(this);
  this.c1o_1 = window_0;
  this.d1o_1 = new WindowMessageQueue(this.c1o_1);
}
protoOf(WindowDispatcher).w1i = function (context, block) {
  return this.d1o_1.n1n(block);
};
protoOf(WindowDispatcher).e1j = function (timeMillis, continuation) {
  var handle = w3cSetTimeout_0(this.c1o_1, WindowDispatcher$scheduleResumeAfterDelay$lambda(continuation, this), delayToInt(timeMillis));
  invokeOnCancellation(continuation, new WindowClearTimeout(this, handle));
};
function delayToInt(timeMillis) {
  return coerceIn(timeMillis, new Long(0, 0), new Long(2147483647, 0)).f1();
}
function ClearTimeout(handle) {
  this.e1o_1 = handle;
}
protoOf(ClearTimeout).d1h = function () {
  w3cClearTimeout_0(this.e1o_1);
};
protoOf(ClearTimeout).j1h = function (cause) {
  this.d1h();
};
protoOf(ClearTimeout).toString = function () {
  return 'ClearTimeout[' + this.e1o_1 + ']';
};
function toDebugString(_this__u8e3s4) {
  return toString(_this__u8e3s4);
}
function get_DefaultDelay() {
  var tmp = Dispatchers_getInstance().z1i_1;
  return isInterface(tmp, Delay) ? tmp : THROW_CCE();
}
function newCoroutineContext(_this__u8e3s4, context) {
  var combined = _this__u8e3s4.d1e().gg(context);
  return !(combined === Dispatchers_getInstance().z1i_1) && combined.o9(Key_instance) == null ? combined.gg(Dispatchers_getInstance().z1i_1) : combined;
}
function get_coroutineName(_this__u8e3s4) {
  return null;
}
function Dispatchers() {
  Dispatchers_instance = this;
  this.z1i_1 = createDefaultDispatcher();
  this.a1j_1 = Unconfined_getInstance();
  this.b1j_1 = new JsMainDispatcher(this.z1i_1, false);
  this.c1j_1 = null;
}
protoOf(Dispatchers).d1j = function () {
  var tmp0_elvis_lhs = this.c1j_1;
  return tmp0_elvis_lhs == null ? this.b1j_1 : tmp0_elvis_lhs;
};
var Dispatchers_instance;
function Dispatchers_getInstance() {
  if (Dispatchers_instance == null)
    new Dispatchers();
  return Dispatchers_instance;
}
function JsMainDispatcher(delegate, invokeImmediately) {
  MainCoroutineDispatcher.call(this);
  this.g1o_1 = delegate;
  this.h1o_1 = invokeImmediately;
  this.i1o_1 = this.h1o_1 ? this : new JsMainDispatcher(this.g1o_1, true);
}
protoOf(JsMainDispatcher).r1l = function () {
  return this.i1o_1;
};
protoOf(JsMainDispatcher).v1i = function (context) {
  return !this.h1o_1;
};
protoOf(JsMainDispatcher).w1i = function (context, block) {
  return this.g1o_1.w1i(context, block);
};
protoOf(JsMainDispatcher).toString = function () {
  var tmp0_elvis_lhs = this.s1l();
  return tmp0_elvis_lhs == null ? this.g1o_1.toString() : tmp0_elvis_lhs;
};
function JobCancellationException(message, cause, job) {
  CancellationException_init_$Init$(message, cause, this);
  captureStack(this, JobCancellationException);
  this.j1o_1 = job;
}
protoOf(JobCancellationException).toString = function () {
  return protoOf(CancellationException).toString.call(this) + '; job=' + toString(this.j1o_1);
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
      tmp_1 = equals(other.j1o_1, this.j1o_1);
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
  var tmp = imul(imul(getStringHashCode(ensureNotNull(this.message)), 31) + hashCode(this.j1o_1) | 0, 31);
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
  this.o1i_1 = this;
  this.p1i_1 = this;
  this.q1i_1 = false;
}
protoOf(LockFreeLinkedListNode).r1i = function (node, permissionsBitmask) {
  var prev = this.p1i_1;
  var tmp;
  if (prev instanceof ListClosed) {
    tmp = ((prev.n1o_1 & permissionsBitmask) === 0 && prev.r1i(node, permissionsBitmask));
  } else {
    node.o1i_1 = this;
    node.p1i_1 = prev;
    prev.o1i_1 = node;
    this.p1i_1 = node;
    tmp = true;
  }
  return tmp;
};
protoOf(LockFreeLinkedListNode).a1k = function (forbiddenElementsBit) {
  this.r1i(new ListClosed(forbiddenElementsBit), forbiddenElementsBit);
};
protoOf(LockFreeLinkedListNode).s1i = function () {
  if (this.q1i_1)
    return false;
  var prev = this.p1i_1;
  var next = this.o1i_1;
  prev.o1i_1 = next;
  next.p1i_1 = prev;
  this.q1i_1 = true;
  return true;
};
protoOf(LockFreeLinkedListNode).t1i = function (node) {
  if (!(this.o1i_1 === this))
    return false;
  this.r1i(node, -2147483648);
  return true;
};
function ListClosed(forbiddenElementsBitmask) {
  LockFreeLinkedListNode.call(this);
  this.n1o_1 = forbiddenElementsBitmask;
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
  this.s1j_1 = null;
}
protoOf(CommonThreadLocal).t1j = function () {
  var tmp = this.s1j_1;
  return (tmp == null ? true : !(tmp == null)) ? tmp : THROW_CCE();
};
protoOf(CommonThreadLocal).u1j = function (value) {
  this.s1j_1 = value;
};
function commonThreadLocal(name) {
  return new CommonThreadLocal();
}
//region block: post-declaration
protoOf(JobSupport).gg = plus;
protoOf(JobSupport).o9 = get_0;
protoOf(JobSupport).fg = fold;
protoOf(JobSupport).eg = minusKey_0;
protoOf(CoroutineDispatcher).o9 = get;
protoOf(CoroutineDispatcher).eg = minusKey;
//endregion
//region block: init
Active_instance = new Active();
Key_instance_1 = new Key_0();
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
  MutableStateFlow as MutableStateFlow34bfoyvwu8czu,
  CoroutineScope as CoroutineScopefcb5f5dwqcas,
  MainScope as MainScope1gi1r4abhrtmm,
  launch as launch1c91vkjzdi9sd,
};
//endregion

//# sourceMappingURL=kotlinx-coroutines-core.mjs.map
