package br.pucpr.authserver.users

import br.pucpr.authserver.exception.UnsupportedMediaTypeException
import br.pucpr.authserver.files.S3Storage
import br.pucpr.authserver.users.controller.responses.UserResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.xml.bind.annotation.XmlType.DEFAULT

@Service
class AvatarService(val storage: S3Storage)
{
    fun save(user: User, avatar: MultipartFile): String = try {
        val contentType = avatar.contentType!!
        val extension = when (contentType) {
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            else -> throw UnsupportedMediaTypeException("jpeg", "png")

        }

        val name = "${user.id}.$extension"
        storage.save(user, "$FOLDER/$name", avatar)
        name
    }
    catch (exception: Error)
    {
        log.error("Unable to store avatar of user ${user.id}! Using default.", exception)
        DEFAULT_AVATAR
    }

    fun urlFor(path: String) = storage.urlFor("$FOLDER/$path")

    fun getGravatarUrl(email: String): String {
        val md = MessageDigest.getInstance("SHA256")
        val hash = md.digest(email.trim().lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return hash
    }

    // Function to check if Gravatar exists and download it
    fun checkAndDownloadAvatar(user: User): Boolean {

        try {
            //Pego o e-mail do usuário
            val email = user.email



            val hash = getGravatarUrl(email)

            val folder = File(OUTPUT_PATH+"/"+hash)



            if(!folder.exists())
            folder.mkdirs()

            val urlGravatar = URL("https://www.gravatar.com/avatar/$hash?d=404")
            val connection = urlGravatar.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD" // Use HEAD request to check existence

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Gravatar exists, now download the image
                //val imageUrl = URL("https://www.gravatar.com/avatar/$hash") // Full URL for image download
                val imageConnection = urlGravatar.openConnection() as HttpURLConnection
                imageConnection.connect()

                if (imageConnection.responseCode == HttpURLConnection.HTTP_OK) {
                    imageConnection.inputStream.use { input ->
                        //input.
                        FileOutputStream(OUTPUT_PATH+"/"+hash+"/"+user.id+".jpg").use { output ->
                            input.copyTo(output)
                        }
                    }

                    storage.saveAvatar(user, OUTPUT_PATH+"/"+hash+"/"+user.id+".jpg")
                    println("Gravatar for $email downloaded")
                    return true
                } else {
                    println("Failed to download Gravatar for $email. Response code: ${imageConnection.responseCode}")
                    return false
                }
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {

                println("No Gravatar found for $email.")
                println("Try to download UiAvatar for $email.")

                return downloadImageUiAvatar(user)


            } else {
                println("Error checking Gravatar for $email. Response code: $responseCode")

                println("Try to download UiAvatar for $email.")

                return downloadImageUiAvatar(user)

            }
        } catch (e: Exception) {
            println("An error occurred: ${e.message}")
            return false
        }
    }

    fun downloadImageUiAvatar(user: User): Boolean
    {
        val email = user.email

        val hash = getGravatarUrl(email)

        //Pego o nome do usuário
        val nameParts = user.name.split(" ")
        //Pego o primeiro nome e o último nome do usuário
        val firstName = nameParts.firstOrNull()
        val lastName = nameParts.getOrNull(nameParts.size - 1)

        val urlUiAvatar = URL("https://ui-avatars.com/api/?name=$firstName+$lastName")

        val connectionUiAvatar = urlUiAvatar.openConnection() as HttpURLConnection
        connectionUiAvatar.requestMethod = "HEAD" // Use HEAD request to check existence

        val responseCodeUiAvatar = connectionUiAvatar.responseCode

        if (responseCodeUiAvatar == HttpURLConnection.HTTP_OK) {
            // Gravatar exists, now download the image
            val imageConnectionUiAvatar = urlUiAvatar.openConnection() as HttpURLConnection
            imageConnectionUiAvatar.connect()

            if (imageConnectionUiAvatar.responseCode == HttpURLConnection.HTTP_OK) {
                imageConnectionUiAvatar.inputStream.use { input ->
                    //input.
                    FileOutputStream(OUTPUT_PATH + "/" + hash + "/" + user.id + ".jpg").use { output ->
                        input.copyTo(output)
                    }
                }

                storage.saveAvatar(user, OUTPUT_PATH + "/" + hash + "/" + user.id + ".jpg")
                println("UIAvatar for $email downloaded")
                return true
            } else {
                println("Failed to download UIAvatar for $email. Response code: ${imageConnectionUiAvatar.responseCode}")
                return false
            }
        }
        else {
            println("Failed to download UIAvatar for $email.")
            return false
        }
    }

    fun deleteAvatar(user: User)
    {
        storage.deleteAvatar("$FOLDER/${user.id}.jpg")
    }



    companion object
    {
        const val OUTPUT_PATH = "C:\\Users\\Victor\\Downloads\\authserver\\avatars"
        const val FOLDER = "avatars"
        const val DEFAULT_AVATAR = "pngtree-black-default-avatar-image_2237212.jpg"
        private val log = LoggerFactory.getLogger(AvatarService::class.java)
    }

}