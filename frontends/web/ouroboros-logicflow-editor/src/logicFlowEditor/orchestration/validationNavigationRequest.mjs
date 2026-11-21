export function readValidationNavigationRequest(props = {}) {
  const formStoreData = props?.formStore?.data || {}

  return {
    navigationTargetNodeName:
      props?.navigationTargetNodeName ?? formStoreData.navigationTargetNodeName ?? null,
    navigationTargetRequestKey:
      props?.navigationTargetRequestKey ?? formStoreData.navigationTargetRequestKey ?? null,
  }
}

export function resolveValidationNavigationNodeName(prevProps = {}, nextProps = {}) {
  const nextNodeName = nextProps?.navigationTargetNodeName
  if (!nextNodeName) return null

  const prevNodeName = prevProps?.navigationTargetNodeName
  if (nextNodeName !== prevNodeName) {
    return nextNodeName
  }

  const nextRequestKey = nextProps?.navigationTargetRequestKey
  const prevRequestKey = prevProps?.navigationTargetRequestKey
  if (nextRequestKey != null && nextRequestKey !== prevRequestKey) {
    return nextNodeName
  }

  return null
}

export function hasPendingValidationNavigationRequest(prevRequest = {}, nextProps = {}) {
  return !!resolveValidationNavigationNodeName(
    prevRequest,
    readValidationNavigationRequest(nextProps)
  )
}
