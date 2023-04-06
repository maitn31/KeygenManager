package com.example.keygenmanager

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
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
        findViewById(android.R.id.content)
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var dateEdt: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val gso =
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.web_client_id))
                .requestEmail()
                .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        //stay logged in
        googleSignInClient.silentSignIn()
            .addOnCompleteListener(
                this
            ) { task -> handleSignInResult(task) }
        auth = Firebase.auth

        val spinnerAuto = findViewById<Spinner>(R.id.spinner_auto)
        val addBtn = findViewById<Button>(R.id.addBtn)
        val deleteBtn = findViewById<Button>(R.id.deleteBtn)
        val signInBtn = findViewById<SignInButton>(R.id.sign_in_button)

        addBtn.setOnClickListener { addToFireBase() }
        deleteBtn.setOnClickListener { deleteFromFirebase() }
        signInBtn.setOnClickListener {
            val currentUser = auth.currentUser
            if (currentUser != null)
                signOut()
            else
                signIn()
        }

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

    override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        if (currentUser != null) {
            findViewById<TextView>(R.id.emailTv).text = currentUser.email.toString()
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account: GoogleSignInAccount? = completedTask.getResult(ApiException::class.java)
            if (account != null) {
                try {
                    // Google Sign In was successful, authenticate with Firebase
                    Log.d(TAG, "firebaseAuthWithGoogle:" + account.id)
                    firebaseAuthWithGoogle(account.idToken!!)
                    val emailTv = findViewById<TextView>(R.id.emailTv)
                    emailTv.text = account.email.toString()
                    Log.d(TAG, account.email.toString())
                } catch (e: ApiException) {
                    // Google Sign In failed, update UI appropriately
                    Log.w(TAG, "Google sign in failed", e)
                }
            }
        } catch (e: ApiException) {
            Log.w(TAG, e.toString())
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success")

                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                }
            }
    }

    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        resultLauncher.launch(signInIntent)
    }

    private fun signOut() {
        googleSignInClient.signOut()
            .addOnCompleteListener(this) {
                Firebase.auth.signOut()
                findViewById<TextView>(R.id.emailTv).text = ""
            }
    }


    private var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // There are no request codes
                val data: Intent? = result.data
                Snackbar.make(parentView, "Sign In Successfully. Press again to Sign Out", Snackbar.LENGTH_LONG).show()
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                handleSignInResult(task)
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
        Snackbar.make(
            parentView,
            "Added $name to database. Valid until $valid",
            Snackbar.LENGTH_LONG
        ).show()

    }

    private fun deleteFromFirebase() {
        val autoType = findViewById<Spinner>(R.id.spinner_auto).selectedItem.toString()
        val name = findViewById<MultiAutoCompleteTextView>(R.id.nameText).text.toString()
        val database = Firebase.database
        val myRef = database.getReference("users/$autoType/$name")
        myRef.removeValue()
        Snackbar.make(parentView, "Removed $name from database.", Snackbar.LENGTH_LONG).show()
    }

    companion object {
        private const val TAG = "GoogleActivity"
    }

}