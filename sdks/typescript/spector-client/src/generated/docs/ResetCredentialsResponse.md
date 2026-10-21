
# ResetCredentialsResponse


## Properties

Name | Type
------------ | -------------
`accountId` | string
`passwordReset` | boolean
`refreshTokensRevoked` | boolean
`revokedApiKeyCount` | number
`resetAt` | Date

## Example

```typescript
import type { ResetCredentialsResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "accountId": null,
  "passwordReset": null,
  "refreshTokensRevoked": null,
  "revokedApiKeyCount": null,
  "resetAt": null,
} satisfies ResetCredentialsResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ResetCredentialsResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


