package sirjin.machine.services

import java.util.Date
import java.util.Properties

import javax.mail._
import javax.mail.internet._

class MailAgent(
    from: String,
    to: List[String],
    cc: List[String],
    bcc: List[String],
    subject: String,
    content: String,
    smtpHost: String,
    pwd: String,
) {

  def createMessage: MimeMessage = {
    val properties = new Properties()
    properties.put("mail.smtp.host", smtpHost)
    properties.put("mail.smtp.port", "25")
    properties.put("mail.smtp.starttls.enable", "true")
    properties.put("mail.smtp.starttls.enable", "true")
    properties.put("mail.debug", "true")

    val auth = new Authenticator() {
      override def getPasswordAuthentication: PasswordAuthentication = {
        new PasswordAuthentication(from, pwd)
      }
    }

    val session = Session.getDefaultInstance(properties, auth)
    new MimeMessage(session)
  }

  def sendMessage: String = {
    try {
      val message: MimeMessage = createMessage
      message.setFrom(new InternetAddress(from))
      setToCcBccRecipients(message)

      message.setSentDate(new Date())
      message.setSubject(subject)

      message.setHeader("Content-Type", "text/html; charset=\"UTF-8\"")
      message.setHeader("Content-Transfer-Encoding", "7bit")

      message.setContent(content, "text/html; charset=\"UTF-8\"")

      Transport.send(message)
      // CommonFunc.func.sendEmail(from, message, pwd, client)
      "success"
    } catch {
      case e: Exception => e.printStackTrace(); "fail"
    }
  }

  def setToCcBccRecipients(message: Message): Unit = {
    val to_addresses: Array[Address]  = to.map(address => new InternetAddress(address)).toArray
    val cc_addresses: Array[Address]  = cc.map(address => new InternetAddress(address)).toArray
    val bcc_addresses: Array[Address] = bcc.map(address => new InternetAddress(address)).toArray

    message.addRecipients(Message.RecipientType.TO, to_addresses)
    message.addRecipients(Message.RecipientType.CC, cc_addresses)
    message.addRecipients(Message.RecipientType.BCC, bcc_addresses)
  }
}
