package controllers

import play.api.mvc.Controller
import play.api.mvc.{ Action, Flash }
import play.api.data.Form
import play.api.i18n._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import models.User
import jp.t2v.lab.play2.auth.AuthElement
import models.Permission
import General.searchForm

object Users extends Controller with AuthElement with AuthConfigImpl {
  val passMap = mapping(
    "pass" -> nonEmptyText,
    "passRepeat" -> nonEmptyText)((pass, passRepeat) =>
      if (pass equals passRepeat) Some(pass)
      else None)(pass => Some("", "")).verifying("Введенные пароли не совпадают!", _ isDefined)

  val userForm = Form {
    mapping(
      "id" -> longNumber,
      "name" -> nonEmptyText,
      "fullName" -> nonEmptyText,
      "edEv" -> boolean,
      "edS" -> boolean,
      "edU" -> boolean) { (id, name, fullName, edEv, edS, edU) =>
        User(id, name, fullName, "123456", edEv, edS, edU, true, true)
      }(user => Some(user.id, user.name, user.fullName, user.canEditEvents, user.canEditSales, user.canEditUsers))
  }

  val passChangeForm = Form {
    mapping(
      "oldPassword" -> nonEmptyText,
      "newPassword" -> passMap)((oldPass, newPass) => (oldPass, newPass))(passwords => Some("", Some("")))
  }

  def newUser = StackAction(AuthorityKey -> Permission.editUsers) { implicit request =>
    implicit val user = Some(loggedIn)
    Ok(views.html.users.addNew(userForm))
  }

  def saveUser = StackAction(AuthorityKey -> Permission.editUsers) { implicit request =>
    implicit val user = Some(loggedIn)
    userForm.bindFromRequest.fold(
      formWithErrors => Ok(views.html.users.addNew(formWithErrors)),
      newUser => {
        if(!User.findByName(newUser.name).isDefined){
	        User.addNew(newUser)
	        Redirect(routes.Events.list).flashing("success" -> ("Новый пользователь " + newUser.name + " успешно создан!"))          
        }else{
          Ok(views.html.users.addNew(userForm.fill(newUser).withError("name", "Пользователь с таким именем уже существует", Nil)))
        }

      })
  }

  def list = StackAction(AuthorityKey -> Permission.editUsers) { implicit request =>
    implicit val user = Some(loggedIn)
    val users = User.getAll
    Ok(views.html.users.list(users, searchForm))
  }

  def show(userID: Long) = StackAction(AuthorityKey -> Permission.editUsers) { implicit request =>
    implicit val loggeduser = Some(loggedIn)
    User.findByID(userID) match {
      case None => Redirect(routes.Users.list).flashing("error" -> "Пользователь с таким ID не найден")
      case Some(user) => Ok(views.html.users.details(user))
    }
  }
  def changePassword = StackAction(AuthorityKey -> Permission.anyUser) { implicit request =>
    implicit val user = Some(loggedIn)
    Ok(views.html.users.changePass(passChangeForm))
  }

  def setPassword = StackAction(AuthorityKey -> Permission.anyUser) { implicit request =>
    implicit val user = Some(loggedIn)
    passChangeForm.bindFromRequest.fold(
      formWithErrors => Ok(views.html.users.changePass(formWithErrors)),
      newPass => if (User.checkPass(newPass._1, user.get.id)) {
        User.changePassword(newPass._2.get, user.get.id)
        Redirect(routes.Events.list).flashing("success" -> "Пароль успешно изменен")
      } else
        Redirect(routes.Users.changePassword).flashing("error" -> "Введен неправильный пароль! Попробуйте еще раз"))
  }
  def editUser(userID: Long) = StackAction(AuthorityKey -> Permission.editUsers) { implicit request =>
    implicit val loggedUser = Some(loggedIn)
    try{
      Ok(views.html.users.edit(userForm.fill(User.findByID(userID).get)))
    }catch{
      case e: NoSuchElementException => BadRequest(views.html.badRequest(request.flash))
    }
  }
  def updateUser = StackAction(AuthorityKey -> Permission.editUsers) { implicit request =>
    implicit val user = Some(loggedIn)
    userForm.bindFromRequest.fold(
      formWithErrors => Ok(views.html.users.edit(formWithErrors)),
      user => {
        User.updateUser(user)
        Redirect(routes.Users.list).flashing("success" -> "Сохранено!")
      })
  }
  def resetPassword(userID: Long) = StackAction(AuthorityKey -> Permission.editUsers) { implicit request =>
    User.resetPassword(userID)
    Redirect(routes.Users.show(userID)).flashing("success" -> "Пароль пользователя сброшен!")
  }

  def disableUser(userID: Long) = StackAction(AuthorityKey -> Permission.editUsers) { implicit request =>
    User.disableUser(userID)
    Redirect(routes.Users.show(userID)).flashing("success" -> "Пользователь блокирован!")
  }

  def enableUser(userID: Long) = StackAction(AuthorityKey -> Permission.editUsers) { implicit request =>
    User.enableUser(userID)
    Redirect(routes.Users.show(userID)).flashing("success" -> "Пользователь разблокирован!")
  }

  def search = StackAction(AuthorityKey -> Permission.editUsers) { implicit request =>
    implicit val user = Some(loggedIn)
    searchForm.bindFromRequest.fold(
      formWithErrors => Redirect(routes.Users.list),
      searchVal => {
        try {
          val userID = searchVal.toLong
          User.findByID(userID) match {
            case None => Redirect(routes.Users.list).flashing("error" -> "Пользователь с таким ID не найден")
            case Some(user) => Redirect(routes.Users.show(userID))
          }
        } catch {
          case e: java.lang.NumberFormatException =>
            val users = User.search(searchVal)
            if (users.isEmpty)
              Redirect(routes.Users.list).flashing("error" -> "По Вашему запросу ничего не найдено")
            else
              Ok(views.html.users.list(users, searchForm))
        }
      })
  }
}