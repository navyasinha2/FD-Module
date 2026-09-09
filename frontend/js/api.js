// Backend is a separate app (dev/backend/fdservice) on its own origin — every
// call here goes over CORS with a full URL, never a relative path.
const API_BASE_URL = 'http://localhost:8080';

/**
 * POSTs a CreateFdAccountRequest-shaped payload to the backend.
 * Resolves with the parsed FdAccount response on 2xx.
 * Rejects with an Error whose `.body` is the parsed ErrorResponse ({code, message})
 * when the backend responds with a non-2xx status, or a plain Error on network failure.
 */
async function createFdAccount(payload) {
  let response;
  try {
    response = await fetch(`${API_BASE_URL}/fd-accounts`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
  } catch (networkError) {
    throw new Error(
      `Could not reach the backend at ${API_BASE_URL}. Is fd-service running? (${networkError.message})`
    );
  }

  const responseBody = await response.json().catch(() => null);

  if (!response.ok) {
    const error = new Error(
      responseBody ? `${responseBody.code}: ${responseBody.message}` : `Request failed with status ${response.status}`
    );
    error.body = responseBody;
    error.status = response.status;
    throw error;
  }

  return responseBody;
}
