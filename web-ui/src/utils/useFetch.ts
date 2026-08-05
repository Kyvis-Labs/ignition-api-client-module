import { useState, useEffect, useCallback, useRef } from 'react';

const useFetch = (
  url,
  headers,
  mapper: undefined | ((jsonObj) => any) = undefined,
  defaultValues = {},
  pollInterval = 0
) => {
  const [data, setData] = useState<any>(defaultValues);
  const dataRef = useRef(data);
  const [error, setError] = useState<Error | null>(null);
  const [refreshCount, setRefreshCount] = useState(0);

  const refresh = useCallback(() => {
    setRefreshCount((prevCount) => prevCount + 1);
  }, []);

  useEffect(() => {
    let isMounted = true;

    const fetchData = async () => {
      try {
        const response = await fetch(`${url}`, {
          method: 'GET',
          headers: headers,
        });
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        const jsonObj: any = await response.json();
        const result: any = mapper ? mapper(jsonObj) : jsonObj;

        if (isMounted && JSON.stringify(result) !== JSON.stringify(dataRef.current)) {
          setData(result);
          dataRef.current = result;
        }

        setError(null);
      } catch (err: any) {
        if (isMounted) {
          setError(err);
        }
      }
    };

    fetchData();

    let intervalId: ReturnType<typeof setInterval> | null = null;
    if (pollInterval) {
      intervalId = setInterval(fetchData, pollInterval);
    }

    return () => {
      isMounted = false;
      if (intervalId) {
        clearInterval(intervalId);
      }
    };
    // refreshCount is intentionally in the deps - it's a pure trigger bumped by refresh() to force
    // an immediate re-fetch (e.g. right after creating/editing an API) instead of waiting up to
    // pollInterval ms for the next scheduled poll.
  }, [url, headers, mapper, pollInterval, refreshCount]);

  return { data, error, refresh };
};

export default useFetch;
