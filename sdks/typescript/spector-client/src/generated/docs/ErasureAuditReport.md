
# ErasureAuditReport


## Properties

Name | Type
------------ | -------------
`accountId` | string
`namespaceId` | string
`operator` | string
`erasedAt` | Date
`localFilesDeleted` | number
`localBytesDeleted` | number
`objectStoreKeysDeleted` | number
`replicaPropagationDispatched` | boolean
`replicaAcks` | number
`unreachableReplicas` | Array&lt;string&gt;
`erasureMechanism` | string
`cryptoErasePerformed` | boolean
`destroyedKeyIds` | Array&lt;string&gt;
`incompletenessDisclosure` | string
`uninspectedClasses` | Array&lt;string&gt;

## Example

```typescript
import type { ErasureAuditReport } from ''

// TODO: Update the object below with actual values
const example = {
  "accountId": null,
  "namespaceId": null,
  "operator": null,
  "erasedAt": null,
  "localFilesDeleted": null,
  "localBytesDeleted": null,
  "objectStoreKeysDeleted": null,
  "replicaPropagationDispatched": null,
  "replicaAcks": null,
  "unreachableReplicas": null,
  "erasureMechanism": null,
  "cryptoErasePerformed": null,
  "destroyedKeyIds": null,
  "incompletenessDisclosure": null,
  "uninspectedClasses": null,
} satisfies ErasureAuditReport

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ErasureAuditReport
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


