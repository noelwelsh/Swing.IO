package net.bulbyvr.swing.io

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import fs2.concurrent.Signal
import scala.swing 
import fs2.*
import cats.syntax.all.*
import cats.effect.syntax.all.*
import scala.reflect.TypeTest
import scala.reflect.ClassTag
import swing.event as sevent
import java.awt.Window
sealed class SwingProp[F[_], A] private[io] {
  import SwingProp.*
  def :=[V](v: V): ConstantModifier[F, A, V] =
    ConstantModifier(v)
  def <--[V](vs: Signal[F, V]): SignalModifier[F, A, V] =
    SignalModifier(vs)
  def <--[V](vs: Resource[F, Signal[F, V]]): SignalResourceModifier[F, A, V] =
    SignalResourceModifier(vs)
  def <--[V](v: Resource[F, V]): ResourceModifier[F, A, V] =
    ResourceModifier(v)
  def -->[Ev](listener: Pipe[F, Ev, Nothing]): PipeModifier[F, A, Ev] =
    PipeModifier(listener)
  // Option isn't real, it can't hurt you
  // inline def <--(vs: Signal[F, Option[V]]): OptionSignalModifier[F, E, V] =
  //  OptionSignalModifier(setter, vs)
  // inline def <--(vs: Resource[F, Signal[F, Option[V]]]): OptionSignalResourceModifier[F, E, V] =
  //  OptionSignalResourceModifier(setter, vs)
}

object SwingProp {
  trait Setter[F[_], E, A, V] {
    def set(elem: E, value: V): F[Unit]
  }
  trait Emits[F[_], E, A, Ev] {} 
  final class ConstantModifier[F[_], A, V] private[io] (
      private[io] val value: V
    )
  final class SignalModifier[F[_], A, V] private[io] (
      private[io] val values: Signal[F, V]
    )
  final class SignalResourceModifier[F[_], A, V] private[io] (
      private[io] val values: Resource[F, Signal[F, V]]
    )
  final class ResourceModifier[F[_], A, V] private[io] (
      private[io] val value: Resource[F, V]
    )
  final class OptionSignalModifier[F[_], A, V] private[io] (
      private[io] val values: Signal[F, Option[V]]
    )
  final class OptionSignalResourceModifier[F[_], A, V] private[io] (
      private[io] val values: Resource[F, Signal[F, Option[V]]]
    )
  final class PipeModifier[F[_], A, Ev] private[io] (
    private[io] val sink: Pipe[F, Ev, Nothing]

    )
  private[io] def listener[F[_], T, Raw](target: Reactor[F], wrapper: Raw => T)
  (using F: Async[F], T: TypeTest[swing.event.Event, Raw]): Stream[F, T] =
    Stream.eval {
      F.async[T] { cb => 
        for {
          fn <- F.delay[PartialFunction[swing.event.Event, Unit]] {
            {
                case e: Raw =>
                  cb(Right(wrapper(e)))
                
            }
          }
        
          _ <- target.addReaction(fn)
          res <- F.delay[Option[F[Unit]]] { Some(F.delay(target.rmReaction(fn))) }
        } yield res

      }
    }.repeat
}

private trait PropModifiers[F[_]](using F: Async[F]) {
  import SwingProp.*
  given forConstantProp[A, E, V](using S: Setter[F, E, A, V]): Modifier[F, E, ConstantModifier[F, A, V]] =
    (m, n) => Resource.eval(S.set(n, m.value))
  given forSignalProp[E, A, V](using S: Setter[F, E, A, V]): Modifier[F, E, SignalModifier[F, A, V]] =
    Modifier.forSignal[F, E, SignalModifier[F, A, V], V](_.values) { (m, n) => 
      it => S.set(n, it)
    }
  given forSignalResource[E, A, V](using S: Setter[F, E, A, V]): Modifier[F, E, SignalResourceModifier[F, A, V]] =
    Modifier.forSignalResource[F, E, SignalResourceModifier[F, A, V], V](_.values) {
      (m, n) => it => S.set(n, it)
    }
  given forResource[E, A, V](using S: Setter[F, E, A, V]): Modifier[F, E, ResourceModifier[F, A, V]] =
    (m, n) => m.value.map(S.set(n, _))

  given forPipeEventProp[E <: UIElement[F], A, Ev](using E: Emits[F, E, A, Ev], EW: EventWrapper[Ev], T: TypeTest[swing.event.Event, EW.RawEv])
  : Modifier[F, E, PipeModifier[F, A, Ev]] = 
    (m, t) => (F.cede *> listener[F, Ev, EW.RawEv](t, EW.wrap).through(m.sink).compile.drain).background.void

}

private trait Props[F[_]](using F: Swing[F], A: Async[F]) {
  import SwingProp.*
  def prop[A]: SwingProp[F, A] =
    SwingProp[F, A]
  given textBtn[E <: AbstractButton[F]]: Setter[F, E, "text", String] =
    (e, v) => e.text.set(v)
  given labelText[E <: Label[F]]: Setter[F, E, "text", String] =
    (e, v) => e.text.set(v)
  given txtCompText[E <: TextComponent[F]]: Setter[F, E, "text", String] =
    (e, v) => e.text.set(v)
  lazy val text: SwingProp[F, "text"] = prop["text"]
  given richWindowTitle[E <: RichWindow[F]]: Setter[F, E, "title", String] =
    (e, v) => e.title.set(v)
  lazy val title: SwingProp[F, "title"] = prop["title"]
  given rootPanelChild[E <: RootPanel[F]]: Setter[F, E, "child", Component[F]] =
    (e, v) => e.child.set(Some(v))
  lazy val child: SwingProp[F, "child"] =
    prop["child"]
  given btnClick[E <: AbstractButton[F]]: Emits[F, E, "onClick", ButtonClicked[F]] = new Emits {}
  // given compClick[E <: Component[F]]: Emits[F, E, "onClick", MouseClicked[F], swing.event.MouseClicked] =
  //  MouseClicked.apply(_)
  lazy val onClick: SwingProp[F, "onClick"] =
    prop["onClick"]
  given txtValueChanged[E <: TextComponent[F]]: Emits[F, E, "onValueChanged", ValueChanged[F]] = new Emits{}
  lazy val onValueChanged: SwingProp[F, "onValueChanged"] =
    prop["onValueChanged"]

}





