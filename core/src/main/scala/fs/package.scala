package sfs

import api._

package object fs {

  implicit def Wrapped(u: Filesystem) = new WrappedFileSystem { val fs: u.type = u }

  // This file exists to experiment with transforming parts of the file system, in this case the Path
  trait WrappedFileSystem {

    val fs: Filesystem

    import fs._

    def map(f: Metadata => Metadata) =
      new Filesystem {
        type Path = fs.Path
        type Data = fs.Data

        def resolve(path: Path) =
          f(fs resolve path).only[fs.Node] map {
            case fs.File(data)   => File(data.get)
            case fs.Dir (kids)   => Dir (kids.get)
            case fs.Link(target) => Link(target)
            case fs.NoNode       => NoNode
          }

        def update(path: Path, metadata: Metadata): Unit =
          // did not implement te reverse map here
          fs update (path, metadata)
      }

    def contraMap[T](toNewPath: fs.Path => T, fromNewPath: T => fs.Path) =
      new Filesystem {
        type Path = T
        type Data   = fs.Data

        def resolve(path: Path) =
          (fs resolve fromNewPath(path)).only[fs.Node] map {
            case fs.File(data)   => File(data.get)
            case fs.Dir (kids)   => Dir (kids.get mapValues toNewPath)
            case fs.Link(target) => Link(toNewPath(target))
            case fs.NoNode       => NoNode
          }

        def update(path: Path, metadata: Metadata): Unit = {
          val p = fromNewPath(path)
          val m = metadata.only[Node] map {
            case File(data)   => fs.File(data.get)
            case Dir (kids)   => fs.Dir(kids.get mapValues(fromNewPath))
            case Link(target) => fs.Link(fromNewPath(target))
            case NoNode       => fs.NoNode
          }
          fs update (p, m)
        }
      }

    def withMappedPath[T](toNewPath: Path => T, fromNewPath: T => Path) =
      contraMap(toNewPath, fromNewPath)

    def mapNode(f: fs.Node =?> fs.Node) = map(_.only[fs.Node] mapOnly f)

    def filter(p: fs.Path => Boolean) = mapNode {
      case dir: fs.Dir => dir filter p
    }
    def filterNot(p: fs.Path => Boolean) = filter(x => !p(x))

  }
}
