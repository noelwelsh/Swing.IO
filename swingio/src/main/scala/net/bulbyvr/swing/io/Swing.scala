package net.bulbyvr.swing.io

import cats.effect.IO
import cats.effect.kernel.{Async, Ref}
import scala.swing
import scala.reflect.TypeTest
import cats.effect.syntax.all.*
import cats.syntax.all.*

opaque type Swing[F[_]] = Async[F]

object Swing {
  implicit inline def forIO: Swing[IO] = IO.asyncForIO
  implicit inline def forAsync[F[_]](using F: Async[F]): Swing[F] = F
}

trait SwingTag[F[_], E] private[io] (name: String)(using F: Async[F]) {
  protected def unsafeBuild: E
  private def build = F.delay(unsafeBuild)

}
opaque type UIElement[F[_]] <: Publisher[F] = swing.UIElement
opaque type Component[F[_]] <: UIElement[F] = swing.Component
object Component {
  extension[F[_]] (component: Component[F]) {
    def enabled(using Swing[F]): Ref[F, Boolean] = {
      new WrappedRef(() => component.enabled, component.enabled = _)
    } 
  }
}
opaque type Container[F[_]] <: UIElement[F] = swing.Container
opaque type ContainerWrapper[F[_]] <: Container[F] = swing.Container.Wrapper

opaque type SequentialContainer[F[_]] <: Container[F] = swing.SequentialContainer
opaque type SeqWrapper[F[_]] <: (SequentialContainer[F] & ContainerWrapper[F]) = swing.SequentialContainer.Wrapper
object SequentialContainer {
  extension[F[_]] (seqContainer: SequentialContainer[F]) {
    def children(using F: Swing[F]): Ref[F, Seq[Component[F]]] = {
      new WrappedRef(() => seqContainer.contents.toSeq, it => {
            seqContainer.contents.clear()
            // you're going to farewell
            seqContainer.contents ++= it
          }
          )
    }
    def append(comp: Component[F])(using F: Swing[F]): F[Unit] = F.delay {
      seqContainer.contents += comp
      ()
    }.evalOn(AwtEventDispatchEC)
    def prepend(comp: Component[F])(using F: Swing[F]): F[Unit] = F.delay {
      seqContainer.contents.prepend(comp)
      ()
    }.evalOn(AwtEventDispatchEC)
  }
}
opaque type Reactor[F[_]] = swing.Reactor
object Reactor {
  extension [F[_]](reactor: Reactor[F]) {
    def addReaction(reaction: swing.Reactions.Reaction)(using F: Swing[F]): F[Unit] =
      F.delay { reactor.reactions += reaction }.evalOn(AwtEventDispatchEC).void
    def rmReaction(reaction: swing.Reactions.Reaction)(using F: Swing[F]): F[Unit] =
      F.delay { reactor.reactions -= reaction }.evalOn(AwtEventDispatchEC).void
  }
}
opaque type Publisher[F[_]] <: Reactor[F] = swing.Publisher

opaque type Panel[F[_]] <: (Component[F] & ContainerWrapper[F]) = swing.Panel
object Panel {
  extension[F[_]] (panel: Panel[F]) {
    def contents(using F: Swing[F]): F[Seq[Component[F]]] = 
      // I'm going to hell
      F.delay(panel.contents).asInstanceOf[F[Seq[Component[F]]]]
  }
}

opaque type RootPanel[F[_]] <: Container[F] = swing.RootPanel
object RootPanel {
  extension[F[_]] (rootPanel: RootPanel[F]) {
    def child(using F: Swing[F]): Ref[F, Option[Component[F]]] = {
      new WrappedRef(() => rootPanel.contents.lift(0), it => {
        it match {
          case Some(value) => rootPanel.contents = value
          // TODO: warn
          case None => ()
        }
      })
    }
  }
}

opaque type Window[F[_]] <: (UIElement[F] & RootPanel[F]) = swing.Window
object Window {
  extension [F[_]](win: Window[F]) {
    def open(using F: Swing[F]): F[Unit] =
      F.delay { win.centerOnScreen(); win.open() }.evalOn(AwtEventDispatchEC)
    def close(using F: Swing[F]): F[Unit] =
      F.delay { win.close() }.evalOn(AwtEventDispatchEC)
  }
}


opaque type MenuBar[F[_]] <: (Component[F] & SeqWrapper[F]) = swing.MenuBar
object MenuBar {
  extension[F[_]] (menuBar: MenuBar[F]) {
    def exists(using F: Swing[F]): F[Boolean] = 
      F.delay { 
        menuBar match {
          case swing.MenuBar.NoMenuBar => false
          case _ => true 
        }
      }
  }
}
opaque type RichWindow[F[_]] <: Window[F] = swing.RichWindow
object RichWindow {
  extension [F[_]](richWindow: RichWindow[F]) {
    def menuBar(using F: Swing[F]): Ref[F, MenuBar[F]] = {
      new WrappedRef(() => richWindow.menuBar, richWindow.menuBar = _)
    }
    def title(using F: Swing[F]): Ref[F, String] = 
      new WrappedRef(() => richWindow.title, richWindow.title = _)
  }
}

opaque type Frame[F[_]] <: RichWindow[F] = swing.Frame

opaque type MainFrame[F[_]] <: Frame[F] = swing.MainFrame

opaque type AbstractButton[F[_]] <: Component[F] = swing.AbstractButton
object AbstractButton {
  extension[F[_]](button: AbstractButton[F]) {
    def text(using F: Swing[F]): Ref[F, String] =
      new WrappedRef(() => button.text, button.text = _)
  }
}

opaque type Button[F[_]] <: AbstractButton[F] = swing.Button

opaque type ToggleButton[F[_]] <: AbstractButton[F] = swing.ToggleButton

opaque type CheckBox[F[_]] <: ToggleButton[F] = swing.CheckBox

opaque type RadioButton[F[_]] <: ToggleButton[F] = swing.RadioButton

opaque type MenuItem[F[_]] <: AbstractButton[F] = swing.MenuItem

opaque type Menu[F[_]] <: MenuItem[F] = swing.Menu

opaque type RadioMenuItem[F[_]] <: MenuItem[F] = swing.RadioMenuItem

opaque type CheckMenuItem[F[_]] <: MenuItem[F] = swing.CheckMenuItem

opaque type ComboBox[F[_], A] <: Component[F] = helpers.MutableComboBox[A]
object ComboBox {
  extension[F[_], A] (comboBox: ComboBox[F, A]) {
    def clear(using F: Swing[F]): F[Unit] = F.delay { comboBox.items.removeAllElements() }.evalOn(AwtEventDispatchEC)
    def addAll(elems: TraversableOnce[A])(using F: Swing[F]): F[Unit] = F.delay { comboBox.items ++= elems }.evalOn(AwtEventDispatchEC)
    def ++=(elems: TraversableOnce[A])(using F: Swing[F]): F[Unit] = comboBox.addAll(elems)
    def add(elem: A)(using F: Swing[F]): F[Unit] = F.delay { comboBox.items += elem }.evalOn(AwtEventDispatchEC)
    def +=(elem: A)(using F: Swing[F]): F[Unit] = comboBox.add(elem)
    def value(using F: Swing[F]): Ref[F, A] = 
      new WrappedRef(() => comboBox.selection.item, comboBox.selection.item = _)
    def renderer(using F: Swing[F]): Ref[F, ItemRenderer[F, A]] = 
      new WrappedRef(() => comboBox.renderer, comboBox.renderer = _)

  }
}
opaque type ItemRenderer[F[_], -A] = swing.ListView.Renderer[A]
opaque type Label[F[_]] <: Component[F] = swing.Label
object Label {
  extension[F[_]] (label: Label[F]) {
    def text(using F: Swing[F]): Ref[F, String] =
      new WrappedRef(() => label.text, label.text = _)
  }
}


opaque type FlowPanel[F[_]] <: (Panel[F] & SeqWrapper[F]) = swing.FlowPanel

opaque type BoxPanel[F[_]] <: (Panel[F] & SeqWrapper[F]) = swing.BoxPanel

opaque type ListView[F[_], A] <: Component[F] = swing.ListView[A]

opaque type TextComponent[F[_]] <: Component[F] = swing.TextComponent
object TextComponent {
  extension[F[_]] (tc: TextComponent[F]) {
    def text(using F: Swing[F]): Ref[F, String] =
      new WrappedRef(() => tc.text, tc.text = _)
  }
}

opaque type TextField[F[_]] <: TextComponent[F] = swing.TextField
