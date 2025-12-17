# WooCommerce Food API Documentation

Complete API reference for integrating with WooCommerce Food plugin from Android applications.

## Authentication

All POST endpoints require WooCommerce REST API authentication using Consumer Key and Consumer Secret.

### Generating API Keys
1. Go to WordPress Admin → WooCommerce → Settings → Advanced → REST API
2. Click "Add Key"
3. Set permissions to "Read/Write"
4. Save and copy the Consumer Key and Consumer Secret

### Authentication Methods

#### Basic Authentication (Recommended for HTTPS)
```
Authorization: Basic base64(consumer_key:consumer_secret)
```

#### Query Parameters (For testing only)
```
?consumer_key=ck_xxxxx&consumer_secret=cs_xxxxx
```

---

## Location API

### 1. Get All Locations

**Endpoint:** `GET /wp-json/exfood-api/v1/locations`

**Authentication:** None (Public)

**Description:** Retrieves list of all configured restaurant locations.

**Response:**
```json
[
  {
    "id": 123,
    "name": "Downtown Branch",
    "slug": "downtown-branch",
    "address": "123 Main St, City, State 12345"
  },
  {
    "id": 124,
    "name": "Airport Branch",
    "slug": "airport-branch",
    "address": "456 Airport Rd, City, State 12346"
  }
]
```

**Android Example (Kotlin + Retrofit):**

```kotlin
// API Interface
interface ExFoodApi {
    @GET("exfood-api/v1/locations")
    suspend fun getLocations(): List<Location>
}

// Data Model
data class Location(
    val id: Int,
    val name: String,
    val slug: String,
    val address: String
)

// Usage
class LocationRepository(private val api: ExFoodApi) {
    suspend fun fetchLocations(): Result<List<Location>> {
        return try {
            val locations = api.getLocations()
            Result.success(locations)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// ViewModel
class LocationViewModel(private val repository: LocationRepository) : ViewModel() {
    private val _locations = MutableLiveData<List<Location>>()
    val locations: LiveData<List<Location>> = _locations
    
    fun loadLocations() {
        viewModelScope.launch {
            repository.fetchLocations().onSuccess { 
                _locations.value = it 
            }
        }
    }
}
```

---

### 2. Filter Orders by Location

**Endpoint:** `GET /wp-json/wc/v3/orders?exwoofood_location={slug}`

**Authentication:** WooCommerce API Keys (Read permission)

**Parameters:**
- `exwoofood_location` (string): Location slug to filter by

**Example Request:**
```
GET /wp-json/wc/v3/orders?exwoofood_location=downtown-branch
Authorization: Basic <base64_credentials>
```

**Response:** Standard WooCommerce orders array filtered by location

**Android Example (Kotlin + Retrofit + OkHttp):**

```kotlin
// OkHttp Interceptor for WooCommerce Auth
class WooCommerceAuthInterceptor(
    private val consumerKey: String,
    private val consumerSecret: String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val credentials = "$consumerKey:$consumerSecret"
        val auth = "Basic " + Base64.encodeToString(
            credentials.toByteArray(), 
            Base64.NO_WRAP
        )
        
        val request = original.newBuilder()
            .header("Authorization", auth)
            .build()
            
        return chain.proceed(request)
    }
}

// API Interface
interface WooCommerceApi {
    @GET("wc/v3/orders")
    suspend fun getOrders(
        @Query("exwoofood_location") locationSlug: String? = null,
        @Query("per_page") perPage: Int = 10,
        @Query("page") page: Int = 1
    ): List<Order>
}

// Retrofit Setup
object ApiClient {
    private const val BASE_URL = "https://yoursite.com/wp-json/"
    private const val CONSUMER_KEY = "ck_xxxxxxxxxxxxx"
    private const val CONSUMER_SECRET = "cs_xxxxxxxxxxxxx"
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(WooCommerceAuthInterceptor(CONSUMER_KEY, CONSUMER_SECRET))
        .build()
    
    val wooApi: WooCommerceApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(WooCommerceApi::class.java)
}

// Usage
class OrderRepository(private val api: WooCommerceApi) {
    suspend fun getOrdersByLocation(locationSlug: String): List<Order> {
        return api.getOrders(exwoofood_location = locationSlug)
    }
}
```

---

## Store Status API

### 1. Get Store Status

**Endpoint:** `GET /wp-json/exfood-api/v1/store-status`

**Authentication:** None (Public)

**Description:** Retrieves current store open/close status.

**Response:**
```json
{
  "status": "enable",
  "raw_value": "enable"
}
```

**Status Values:**
- `disable` - Store always open (ignores open hours)
- `enable` - Follow configured open hours
- `closed` - Store always closed

**Android Example:**

```kotlin
// Data Model
data class StoreStatus(
    val status: String,
    @SerializedName("raw_value") 
    val rawValue: String
) {
    val isOpen: Boolean
        get() = status != "closed"
    
    val statusText: String
        get() = when (status) {
            "disable" -> "Always Open"
            "enable" -> "Following Hours"
            "closed" -> "Closed"
            else -> "Unknown"
        }
}

// API Interface
interface ExFoodApi {
    @GET("exfood-api/v1/store-status")
    suspend fun getStoreStatus(): StoreStatus
}

// Repository
class StoreRepository(private val api: ExFoodApi) {
    suspend fun checkStoreStatus(): StoreStatus {
        return api.getStoreStatus()
    }
}

// ViewModel with LiveData
class StoreViewModel(private val repository: StoreRepository) : ViewModel() {
    private val _storeStatus = MutableLiveData<StoreStatus>()
    val storeStatus: LiveData<StoreStatus> = _storeStatus
    
    fun refreshStatus() {
        viewModelScope.launch {
            try {
                val status = repository.checkStoreStatus()
                _storeStatus.value = status
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
```

---

### 2. Update Store Status

**Endpoint:** `POST /wp-json/exfood-api/v1/store-status`

**Authentication:** WooCommerce API Keys (Write permission + `manage_woocommerce` capability)

**Request Body:**
```json
{
  "status": "closed"
}
```

**Allowed Values:** `disable`, `enable`, `closed`

**Response:**
```json
{
  "success": true,
  "message": "Store status updated successfully.",
  "new_status": "closed",
  "internal_value": "closed"
}
```

**Error Response (401/403):**
```json
{
  "code": "rest_forbidden",
  "message": "Sorry, you are not allowed to do that.",
  "data": {
    "status": 403
  }
}
```

**Android Example:**

```kotlin
// Request Model
data class UpdateStatusRequest(
    val status: String
)

// Response Model
data class UpdateStatusResponse(
    val success: Boolean,
    val message: String,
    @SerializedName("new_status")
    val newStatus: String,
    @SerializedName("internal_value")
    val internalValue: String
)

// API Interface
interface ExFoodApi {
    @POST("exfood-api/v1/store-status")
    suspend fun updateStoreStatus(
        @Body request: UpdateStatusRequest
    ): UpdateStatusResponse
}

// Repository
class StoreRepository(private val api: ExFoodApi) {
    suspend fun setStoreStatus(status: String): Result<UpdateStatusResponse> {
        return try {
            val response = api.updateStoreStatus(
                UpdateStatusRequest(status)
            )
            Result.success(response)
        } catch (e: HttpException) {
            if (e.code() == 403) {
                Result.failure(Exception("Unauthorized: Check API credentials"))
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// ViewModel
class StoreControlViewModel(private val repository: StoreRepository) : ViewModel() {
    fun closeStore() {
        viewModelScope.launch {
            repository.setStoreStatus("closed").onSuccess {
                // Show success message
            }.onFailure {
                // Show error
            }
        }
    }
    
    fun openStore() {
        viewModelScope.launch {
            repository.setStoreStatus("enable")
        }
    }
}
```

---

## Complete Android Integration Example

### Gradle Dependencies
```gradle
dependencies {
    // Retrofit
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    
    // OkHttp
    implementation 'com.squareup.okhttp3:okhttp:4.11.0'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.11.0'
    
    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1'
    
    // ViewModel & LiveData
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2'
    implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.6.2'
}
```

### Complete API Client Setup

```kotlin
object WooFoodApiClient {
    private const val BASE_URL = "https://yoursite.com/wp-json/"
    private const val CONSUMER_KEY = "ck_xxxxxxxxxxxxx"
    private const val CONSUMER_SECRET = "cs_xxxxxxxxxxxxx"
    
    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val credentials = "$CONSUMER_KEY:$CONSUMER_SECRET"
        val auth = "Basic " + Base64.encodeToString(
            credentials.toByteArray(), 
            Base64.NO_WRAP
        )
        
        val request = original.newBuilder()
            .header("Authorization", auth)
            .build()
            
        chain.proceed(request)
    }
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val exFoodApi: ExFoodApi = retrofit.create(ExFoodApi::class.java)
    val wooApi: WooCommerceApi = retrofit.create(WooCommerceApi::class.java)
}

// Combined API Interface
interface ExFoodApi {
    @GET("exfood-api/v1/locations")
    suspend fun getLocations(): List<Location>
    
    @GET("exfood-api/v1/store-status")
    suspend fun getStoreStatus(): StoreStatus
    
    @POST("exfood-api/v1/store-status")
    suspend fun updateStoreStatus(@Body request: UpdateStatusRequest): UpdateStatusResponse
}

interface WooCommerceApi {
    @GET("wc/v3/orders")
    suspend fun getOrders(
        @Query("exwoofood_location") locationSlug: String? = null,
        @Query("per_page") perPage: Int = 10,
        @Query("page") page: Int = 1,
        @Query("status") status: String? = null
    ): List<Order>
}
```

### UI Example (Jetpack Compose)

```kotlin
@Composable
fun StoreControlScreen(viewModel: StoreViewModel = viewModel()) {
    val storeStatus by viewModel.storeStatus.observeAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Store Status",
            style = MaterialTheme.typography.h5
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        storeStatus?.let { status ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = when (status.status) {
                    "closed" -> Color.Red
                    "enable" -> Color.Green
                    else -> Color.Gray
                }
            ) {
                Text(
                    text = status.statusText,
                    modifier = Modifier.padding(16.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.h6
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { viewModel.openStore() }) {
                Text("Open Store")
            }
            
            Button(
                onClick = { viewModel.closeStore() },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color.Red
                )
            ) {
                Text("Close Store")
            }
        }
    }
}
```

---

## Error Handling Best Practices

```kotlin
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val exception: Exception) : ApiResult<Nothing>()
    object Loading : ApiResult<Nothing>()
}

class SafeApiRepository(private val api: ExFoodApi) {
    suspend fun <T> safeApiCall(
        apiCall: suspend () -> T
    ): ApiResult<T> {
        return try {
            ApiResult.Success(apiCall())
        } catch (e: HttpException) {
            when (e.code()) {
                401, 403 -> ApiResult.Error(Exception("Authentication failed"))
                404 -> ApiResult.Error(Exception("Endpoint not found. Check if API is enabled."))
                else -> ApiResult.Error(Exception("HTTP ${e.code()}: ${e.message()}"))
            }
        } catch (e: IOException) {
            ApiResult.Error(Exception("Network error: ${e.message}"))
        } catch (e: Exception) {
            ApiResult.Error(e)
        }
    }
}
```

---

## Testing Checklist

- [ ] Verify API endpoints are enabled in WordPress Admin → Woo Food → Addon Settings
- [ ] Generate WooCommerce API keys with Read/Write permissions
- [ ] Test GET endpoints without authentication
- [ ] Test POST endpoints with valid credentials
- [ ] Test POST endpoints with invalid credentials (should return 403)
- [ ] Test with different store status values
- [ ] Test location filtering with valid and invalid slugs
- [ ] Implement proper error handling for network failures
- [ ] Add retry logic for transient failures
- [ ] Implement caching for location list

---

## Security Notes

1. **Never hardcode API keys** - Use BuildConfig or secure storage
2. **Use HTTPS only** - Basic auth sends credentials in base64 (easily decoded)
3. **Validate SSL certificates** - Don't disable certificate validation in production
4. **Store credentials securely** - Use Android Keystore or EncryptedSharedPreferences
5. **Implement token refresh** - If using OAuth instead of Basic Auth

```kotlin
// Secure credential storage example
class SecurePreferences(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "woo_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    fun saveCredentials(key: String, secret: String) {
        sharedPreferences.edit()
            .putString("consumer_key", key)
            .putString("consumer_secret", secret)
            .apply()
    }
    
    fun getConsumerKey(): String? = 
        sharedPreferences.getString("consumer_key", null)
    
    fun getConsumerSecret(): String? = 
        sharedPreferences.getString("consumer_secret", null)
}
```
