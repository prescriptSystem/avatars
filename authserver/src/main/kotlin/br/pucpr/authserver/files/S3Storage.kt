package br.pucpr.authserver.files


import br.pucpr.authserver.users.User
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.DeleteObjectRequest
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.io.File


@Component
class S3Storage {

    private val s3: AmazonS3 = AmazonS3ClientBuilder.standard()
        .withRegion(Regions.US_EAST_1)
        .withCredentials(EnvironmentVariableCredentialsProvider())
        .build()

    fun save(user: User, path: String, file: MultipartFile) : String {
        val contentType = file.contentType!!

        val transferManager = TransferManagerBuilder.standard()
            .withS3Client(s3)
            .build()

        val meta = ObjectMetadata()

        meta.contentType = contentType
        meta.contentLength = file.size
        meta.userMetadata["userId"] = "${user.id}"
        meta.userMetadata["originalFileName"] = file.originalFilename

        transferManager.upload(BUCKET, path, file.inputStream, meta)
            .waitForUploadResult()

        return path
    }

    fun saveAvatar(user: User, path: String) : String {
        val file = File(path)

        val transferManager = TransferManagerBuilder.standard()
            .withS3Client(s3)
            .build()

        val meta = ObjectMetadata()
        println("User $user.email downloaded")
        meta.contentType = "image/jpeg"
        meta.contentLength = file.length()
        meta.userMetadata["userId"] = "${user.id}"
        meta.userMetadata["originalFileName"] = file.name


        transferManager.upload(BUCKET, "avatars/"+file.name, file.inputStream(), meta)
            .waitForUploadResult()

        return path
    }

    fun deleteAvatar(path: String)  {

        try {
            // Create a DeleteObjectRequest
            s3.deleteObject(BUCKET, path)

            //println("Object '$objectKey' deleted successfully from bucket '$bucketName'.")
        } catch (e: AmazonS3Exception) {
            System.err.println("Error deleting object: " + e.message)
        }
    }

    fun urlFor(path: String) = "https://$BUCKET.s3.us-east-1.amazonaws.com/$path"

    companion object{
        private const val BUCKET = "victor-authserver-public"


    }

}