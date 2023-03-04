package net.bulbyvr.swing.io

import cats.effect.{IO, IOApp, ExitCode}
import cats.effect.kernel.Resource
import cats.effect.unsafe.implicits.*
trait IOSwingApp extends IOApp {
  def render: Resource[IO, MainFrame[IO]]

  def run(args: List[String]) = IO.uncancelable { poll => render.flatMap { f => Resource.make(f.open)(_ => f.close) }.useForever }
}

