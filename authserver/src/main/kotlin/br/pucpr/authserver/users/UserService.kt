package br.pucpr.authserver.users

import br.pucpr.authserver.exception.BadRequestException
import br.pucpr.authserver.exception.NotFoundException
import br.pucpr.authserver.roles.RoleRepository
import br.pucpr.authserver.security.Jwt
import br.pucpr.authserver.users.controller.responses.LoginResponse
import br.pucpr.authserver.users.controller.responses.UserResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.security.MessageDigest
import kotlin.jvm.optionals.getOrNull

@Service
class UserService(
    val repository: UserRepository,
    val roleRepository: RoleRepository,
    val avatarService: AvatarService,
    val jwt: Jwt
) {
    fun insert(user: User): User {
        if (repository.findByEmail(user.email) != null) {
            throw BadRequestException("User already exists")
        }

        val savedUser = repository.save(user)
            .also { log.info("User inserted: {}", it.id) }

        if(savedUser.id != null)
        {
            if(avatarService.checkAndDownloadAvatar(user))
            {
                savedUser.avatar = savedUser.id.toString()+".jpg"
                 return repository.save(savedUser)
            }
        }

        savedUser.avatar = "pngtree-black-default-avatar-image_2237212.jpg"
        return repository.save(savedUser)
    }

    fun update(id: Long, name: String): User? {
        val user = findByIdOrThrow(id)
        if (user.name == name) return null
        user.name = name
        return repository.save(user)
    }

    fun findAll(dir: SortDir = SortDir.ASC): List<User> = when (dir) {
        SortDir.ASC -> repository.findAll(Sort.by("name").ascending())
        SortDir.DESC -> repository.findAll(Sort.by("name").descending())
    }

    fun findByRole(role: String): List<User> = repository.findByRole(role)

    fun findByIdOrNull(id: Long) = repository.findById(id).getOrNull()
    private fun findByIdOrThrow(id: Long) =
        findByIdOrNull(id) ?: throw NotFoundException(id)

    fun delete(id: Long): Boolean {
        val user = findByIdOrNull(id) ?: return false
        if (user.roles.any { it.name == "ADMIN" }) {
            val count = repository.findByRole("ADMIN").size
            if (count == 1) throw BadRequestException("Cannot delete the last system admin!")
        }
        avatarService.deleteAvatar(user)
        repository.delete(user)
        log.info("User deleted: {}", user.id)
        return true
    }

    fun addRole(id: Long, roleName: String): Boolean {
        val user = findByIdOrThrow(id)
        if (user.roles.any { it.name == roleName }) return false

        val role = roleRepository.findByName(roleName) ?:
            throw BadRequestException("Invalid role: $roleName")

        user.roles.add(role)
        repository.save(user)
        log.info("Granted role {} to user {}", role.name, user.id)
        return true
    }

    fun login(email: String, password: String): LoginResponse? {
        val user = repository.findByEmail(email) ?: return null
        if (user.password != password) return null

        log.info("User logged in. id={}, name={}", user.id, user.name)
        return LoginResponse(
            token = jwt.createToken(user),
            user = toResponse(user)
        )
    }

    fun saveAvatar(id: Long, avatar: MultipartFile)
    {
        val user = findByIdOrThrow(id)
        user.avatar = avatarService.save(user, avatar)
        repository.save(user)
    }

    fun deleteAvatar(id: Long): Boolean {
        val user = findByIdOrNull(id) ?: return false
        avatarService.deleteAvatar(user)
        log.info("User avatar deleted: {}", user.id)
        return true
    }

    /*fun createGravatarHash(email: String): String {
        val processedEmail = email.trim().lowercase()
        val md = MessageDigest.getInstance("SHA-256") // Gravatar now uses SHA-256
        val digest = md.digest(processedEmail.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }*/

    fun toResponse(user: User) = UserResponse(user, avatarService.urlFor(user.avatar))



    companion object {
        private val log = LoggerFactory.getLogger(UserService::class.java)
    }
}