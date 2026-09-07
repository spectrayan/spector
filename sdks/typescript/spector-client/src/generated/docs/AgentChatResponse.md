
# AgentChatResponse


## Properties

Name | Type
------------ | -------------
`response` | string
`sessionId` | string
`isNewSession` | boolean
`model` | string
`status` | string
`latency` | number
`durationMs` | number
`primedMemories` | number
`trace` | [Array&lt;TraceEvent&gt;](TraceEvent.md)
`pendingToolCalls` | Array&lt;{ [key: string]: any | null; }&gt;
`sources` | Array&lt;string&gt;
`tokenUsage` | [TokenUsageDto](TokenUsageDto.md)

## Example

```typescript
import type { AgentChatResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "response": null,
  "sessionId": null,
  "isNewSession": null,
  "model": null,
  "status": null,
  "latency": null,
  "durationMs": null,
  "primedMemories": null,
  "trace": null,
  "pendingToolCalls": null,
  "sources": null,
  "tokenUsage": null,
} satisfies AgentChatResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AgentChatResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


