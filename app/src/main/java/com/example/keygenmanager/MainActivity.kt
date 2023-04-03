package com.example.keygenmanager

import android.app.Activity
import android.app.DatePickerDialog
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {
    private val parentView: View by lazy {
        findViewById<View>(android.R.id.content)
    }
    private lateinit var auth: FirebaseAuth
    lateinit var dateEdt: EditText
    private lateinit var oneTapClient: SignInClient
    private var showOneTapUI = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = Firebase.auth

        oneTapClient = Identity.getSignInClient(this)

        val spinnerAuto = findViewById<Spinner>(R.id.spinner_auto)
        val addBtn = findViewById<Button>(R.id.addBtn)
        val deleteBtn = findViewById<Button>(R.id.deleteBtn)
        val signInBtn = findViewById<SignInButton>(R.id.sign_in_button)

        addBtn.setOnClickListener { addToFireBase() }
        deleteBtn.setOnClickListener { deleteFromFirebase() }
        signInBtn.setOnClickListener { signIn() }


        ArrayAdapter.createFromResource(
            this,
            R.array.AutoType,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            spinnerAuto.adapter = adapter
        }

        dateEdt = findViewById(R.id.idEdtDate)

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, 30)
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val defaultExpireDate = dateFormat.format(calendar.time)
        dateEdt.setText(defaultExpireDate)

        dateEdt.setOnClickListener {
            val c = Calendar.getInstance()

            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH)
            val day = c.get(Calendar.DAY_OF_MONTH)

            val datePickerDialog = DatePickerDialog(
                this,
                { _, year, monthOfYear, dayOfMonth ->
                    val dat = (dayOfMonth.toString() + "-" + (monthOfYear + 1) + "-" + year)
                    dateEdt.setText(dat)
                },
                year,
                month,
                day
            )
            datePickerDialog.show()
        }
    }

    private fun createSignInRequest(onlyAuthorizedAccounts: Boolean): BeginSignInRequest =
        BeginSignInRequest.builder()
            .setPasswordRequestOptions(
                BeginSignInRequest.PasswordRequestOptions.builder()
                    .setSupported(true)
                    .build()
            )
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    // Your server's client ID, not your Android client ID.
                    .setServerClientId(getString(R.string.web_client_id))
                    // Only show accounts previously used to sign in.
                    .setFilterByAuthorizedAccounts(onlyAuthorizedAccounts)
                    .build()
            )
            .build()

    private fun signIn() {
        val signInRequest = createSignInRequest(onlyAuthorizedAccounts = true)

        oneTapClient
            .beginSignIn(signInRequest)
            .addOnSuccessListener(this) { result ->
                try {

                    resultLauncher.launch(
                        IntentSenderRequest.Builder(result.pendingIntent.intentSender).build())

                } catch (e: IntentSender.SendIntentException) {
                    Log.e(TAG, "Couldn't start One Tap UI: ${e.localizedMessage}")
                }
            }
            .addOnFailureListener(this) { e ->
                Log.d(TAG, e.localizedMessage)
                // No saved credentials found. Launch the One Tap sign-up flow
                signUp()
            }
    }

    private fun signUp() {
        val signUpRequest = createSignInRequest(onlyAuthorizedAccounts = false)

        oneTapClient
            .beginSignIn(signUpRequest)
            .addOnSuccessListener(this) { result ->
                try {
                    resultLauncher.launch(
                        IntentSenderRequest.Builder(result.pendingIntent.intentSender).build())

                } catch (e: IntentSender.SendIntentException) {
                    Log.e(TAG, "Couldn't start One Tap UI: ${e.localizedMessage}")
                }
            }
            .addOnFailureListener(this) { e ->
                Log.d(TAG, e.localizedMessage)
                // No saved credentials found. Show error
                Snackbar.make(parentView, e.localizedMessage, Snackbar.LENGTH_LONG).show()
            }
    }

    private var resultLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // There are no request codes
            val data: Intent? = result.data
            try {
                val credential = oneTapClient.getSignInCredentialFromIntent(data)
                val idToken = credential.googleIdToken
                val username = credential.id
                val password = credential.password

                when {
                    idToken != null -> {
                        // Got an ID token from Google. Use it to authenticate
                        // with your backend.
                        Log.d(TAG, "Got ID token: $idToken")
                        firebaseAuthWithGoogle(idToken)

                    }
                    username != null -> {
                        Log.d(TAG, "Got name: $username")
                    }
                    password != null -> {
                        // Got a saved username and password. Use them to authenticate
                        // with your backend.
                        Log.d(TAG, "Got password: $password")
                    }
                    else -> {
                        // Shouldn't happen.
                        Log.d(TAG, "No ID token or password!")
                    }
                }

            } catch (e: ApiException) {
                when (e.statusCode) {
                    CommonStatusCodes.CANCELED -> {
                        Log.d(TAG, "One-tap dialog was closed.")
                        // Don't re-prompt the user.
                        showOneTapUI = false
                    }
                    CommonStatusCodes.NETWORK_ERROR -> {
                        Log.d(TAG, "One-tap encountered a network error.")
                        // Try again or just ignore.
                    }
                    else -> {
                        Log.d(
                            TAG, "Couldn't get credential from result." +
                                    " (${e.localizedMessage})"
                        )
                    }
                }
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success")
                    val user = auth.currentUser
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                }
            }
    }

    private fun addToFireBase() {
        val autoType = findViewById<Spinner>(R.id.spinner_auto).selectedItem.toString()
        val name = findViewById<MultiAutoCompleteTextView>(R.id.nameText).text.toString()
        val keygen = findViewById<EditText>(R.id.keygenText).text.toString()
        val valid = findViewById<EditText>(R.id.idEdtDate).text.toString()

        val database = Firebase.database
        val myRef = database.getReference("users/$autoType/$name")

        myRef.child("keygen").setValue(keygen)
        myRef.child("valid").setValue(valid)
        Snackbar.make(parentView,"Added $name to database. Valid until $valid",Snackbar.LENGTH_LONG).show()

    }

    private fun deleteFromFirebase() {
        val autoType = findViewById<Spinner>(R.id.spinner_auto).selectedItem.toString()
        val name = findViewById<MultiAutoCompleteTextView>(R.id.nameText).text.toString()
        val database = Firebase.database
        val myRef = database.getReference("users/$autoType/$name")
        myRef.removeValue()
        Snackbar.make(parentView,"Removed $name from database.",Snackbar.LENGTH_LONG).show()
    }


}