package org.s3q

class Bucket(name: String, client: S3Client) {
  val bufferSize = 501
  val refillWhen = bufferSize / 2

  def items = {
    // var done = false
    val keyIterator = keys.elements
    val buffer = new scala.collection.mutable.ListBuffer[(String, S3Response)]

    def fillBuffer = {
      val keys = keyIterator.take(bufferSize).toList
      buffer ++= keys.map { key => (key, get(key)) }
    }

    new Iterator[(String, S3Response)] {
      def hasNext = { !buffer.isEmpty || keyIterator.hasNext }

      def next = {
        if (keyIterator.hasNext && buffer.length < bufferSize ) { fillBuffer }
        buffer.remove(0)
      }
    }
  }

  private def keyStreams = {
    val MAX_BATCH = 1000
    var marker: Option[String] = None
    var done = false

    new Iterator[Iterable[String]] {
      def hasNext = !done

      def next = {
        val response = client.execute(new S3List(client, name, MAX_BATCH, marker))
        val items = response.items
        marker = items.lastOption
        done = !response.isTruncated

        items
      }
    }
  }

  def keys:Iterable[String] = {
    Stream.concat(keyStreams.map(_.toStream))
  }

  def get(key: String) = {
    client.execute(new S3Get(client, name, key))
  }

  // def get(keys: Iterable[String]): Map[String, S3Response] = {
  //   keys.map { client.execute(new S3Get(name, _)).get }.foldLeft(Map()) ( _ ++ (S3Get.))
  // }

}